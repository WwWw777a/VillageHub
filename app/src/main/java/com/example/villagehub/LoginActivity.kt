package com.example.villagehub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.security.MessageDigest

class LoginActivity : AppCompatActivity() {

    private val VILLAGE_LAT = 44.841121
    private val VILLAGE_LON = 37.655546
    private val ALLOWED_RADIUS_METERS = 15000.0

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isUserLoggedIn()) {
            openMainActivity(null)
            return
        }

        setContentView(R.layout.activity_login)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val etName = findViewById<TextInputEditText>(R.id.et_name)
        val etPhone = findViewById<TextInputEditText>(R.id.et_phone)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val etSecretWord = findViewById<TextInputEditText>(R.id.et_secret_word)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val tvForgotPass = findViewById<TextView>(R.id.tv_forgot_pass)

        val tvPrivacy = findViewById<TextView>(R.id.tv_privacy_policy)
        tvPrivacy.setOnClickListener {
            val link = "https://disk.yandex.ru/i/rrD6Mhbe3IEf0Q"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            startActivity(intent)
        }

        btnLogin.setOnClickListener {
            val name = etName.text.toString().trim()
            var rawPhone = etPhone.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val secret = etSecretWord.text.toString().trim()

            if (name.isEmpty() || rawPhone.isEmpty() || password.isEmpty() || secret.isEmpty()) {
                Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- ВАЖНОЕ ИЗМЕНЕНИЕ: ФОРМАТИРУЕМ НОМЕР ПЕРЕД ВХОДОМ ---
            val phone = formatPhoneNumber(rawPhone)
            // --------------------------------------------------------

            if (!isValidPassword(password)) {
                Toast.makeText(this, "Пароль: от 6 до 12 символов", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            checkGlobalGpsSwitch(name, phone, password, secret)
        }

        tvForgotPass.setOnClickListener {
            showRestorePasswordDialog()
        }
    }

    // --- ФУНКЦИЯ ФОРМАТИРОВАНИЯ НОМЕРА (ВСЕГДА ДЕЛАЕТ +7...) ---
    private fun formatPhoneNumber(input: String): String {
        // 1. Убираем все лишнее (скобки, пробелы, тире), оставляем цифры и плюс
        var clean = input.replace(Regex("[^0-9+]"), "")

        // 2. Если начинается с 8 (например 8900...), меняем 8 на +7
        if (clean.startsWith("8")) {
            clean = "+7" + clean.substring(1)
        }
        // 3. Если начинается с 9 (например 900...), добавляем +7
        else if (clean.startsWith("9")) {
            clean = "+7" + clean
        }
        // 4. Если начинается с 7, но без плюса (7900...), добавляем плюс
        else if (clean.startsWith("7")) {
            clean = "+$clean"
        }

        return clean
    }
    // -----------------------------------------------------------

    private fun checkGlobalGpsSwitch(name: String, phone: String, password: String, secret: String) {
        val settingsRef = FirebaseDatabase.getInstance().getReference("AppSettings").child("gpsEnabled")

        Toast.makeText(this, "Вход...", Toast.LENGTH_SHORT).show()

        settingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isGpsOn = snapshot.getValue(Boolean::class.java) ?: true

                if (isGpsOn) {
                    checkLocationAndLogin(name, phone, password, secret)
                } else {
                    checkBanStatus(name, phone, password, secret)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                checkLocationAndLogin(name, phone, password, secret)
            }
        })
    }

    private fun getRoleByPhone(phone: String): String {
        return when (phone) {
            "+7000" -> "АДМИН"
            "+7111" -> "МОДЕРАТОР"
            "+79002712293" -> "АДМИН"
            else -> "Житель"
        }
    }

    private fun hashString(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun checkLocationAndLogin(name: String, phone: String, password: String, secret: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        Toast.makeText(this, "Проверка местоположения...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val villageLoc = Location("VillageCenter")
                villageLoc.latitude = VILLAGE_LAT
                villageLoc.longitude = VILLAGE_LON
                val distance = location.distanceTo(villageLoc)

                if (distance <= ALLOWED_RADIUS_METERS) {
                    checkBanStatus(name, phone, password, secret)
                } else {
                    val distInt = (distance / 1000).toInt()
                    Toast.makeText(this, "Ошибка! Вы в $distInt км от поселка.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Включите GPS!", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка GPS: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkBanStatus(name: String, phone: String, password: String, secret: String) {
        val bannedRef = FirebaseDatabase.getInstance().getReference("BannedUsers").child(phone)

        bannedRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val banTime = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    val banType = snapshot.child("type").getValue(String::class.java) ?: "GLOBAL"
                    val currentTime = System.currentTimeMillis()

                    if (banTime != -1L && banTime < currentTime) {
                        bannedRef.removeValue()
                        checkUserInDatabase(name, phone, password, secret)
                        return
                    }

                    if (banType == "GLOBAL") {
                        if (banTime == -1L) {
                            showBanAlert("Вы заблокированы в приложении НАВСЕГДА.")
                        } else {
                            val minutes = (banTime - currentTime) / (1000 * 60)
                            showBanAlert("Приложение заблокировано. Осталось: $minutes мин.")
                        }
                    } else {
                        checkUserInDatabase(name, phone, password, secret)
                    }
                } else {
                    checkUserInDatabase(name, phone, password, secret)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showBanAlert(message: String) {
        AlertDialog.Builder(this)
            .setTitle("ДОСТУП ЗАПРЕЩЕН ⛔")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Понятно") { _, _ -> }
            .show()
    }

    private fun checkUserInDatabase(name: String, phone: String, password: String, secret: String) {
        val usersRef = FirebaseDatabase.getInstance().getReference("Users")
        val passwordHash = hashString(password)
        val secretHash = hashString(secret)
        val currentTime = System.currentTimeMillis()

        usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val dbPasswordHash = snapshot.child("password").getValue(String::class.java)

                    if (dbPasswordHash == passwordHash) {
                        var role = snapshot.child("role").getValue(String::class.java) ?: "Житель"
                        val dbName = snapshot.child("name").getValue(String::class.java) ?: name

                        val hardcodedRole = getRoleByPhone(phone)
                        if (hardcodedRole == "АДМИН" || hardcodedRole == "МОДЕРАТОР") {
                            role = hardcodedRole
                            usersRef.child(phone).child("role").setValue(role)
                        }

                        usersRef.child(phone).child("lastActive").setValue(currentTime)
                        saveLocalAndStart(dbName, phone, role)
                    } else {
                        Toast.makeText(this@LoginActivity, "Неверный пароль!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val role = getRoleByPhone(phone)
                    val newUser = mapOf(
                        "name" to name,
                        "password" to passwordHash,
                        "role" to role,
                        "secretWord" to secretHash,
                        "regDate" to currentTime,
                        "lastActive" to currentTime
                    )
                    usersRef.child(phone).setValue(newUser)
                    saveLocalAndStart(name, phone, role)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showRestorePasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Восстановление пароля")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val inputPhone = EditText(this)
        inputPhone.hint = "Введите номер телефона" // Убрал подсказку про формат, т.к. теперь автоформат
        layout.addView(inputPhone)
        val inputSecret = EditText(this)
        inputSecret.hint = "Ваше кодовое слово"
        layout.addView(inputSecret)
        val inputNewPass = EditText(this)
        inputNewPass.hint = "Новый пароль"
        inputNewPass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(inputNewPass)

        builder.setView(layout)
        builder.setPositiveButton("Сменить") { _, _ ->
            val rawPhone = inputPhone.text.toString().trim()
            val secret = inputSecret.text.toString().trim()
            val newPass = inputNewPass.text.toString().trim()

            if (rawPhone.isNotEmpty() && secret.isNotEmpty() && newPass.isNotEmpty()) {
                // ТУТ ТОЖЕ ИСПОЛЬЗУЕМ АВТОФОРМАТ
                val phone = formatPhoneNumber(rawPhone)

                if (isValidPassword(newPass)) attemptPasswordReset(phone, secret, newPass)
                else Toast.makeText(this, "Пароль слишком простой!", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun attemptPasswordReset(phone: String, secret: String, newPass: String) {
        val usersRef = FirebaseDatabase.getInstance().getReference("Users")
        val secretHashInput = hashString(secret)
        usersRef.child(phone).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val dbSecretHash = snapshot.child("secretWord").getValue(String::class.java) ?: ""
                    if (dbSecretHash == secretHashInput) {
                        val newPassHash = hashString(newPass)
                        usersRef.child(phone).child("password").setValue(newPassHash)
                        Toast.makeText(this@LoginActivity, "Пароль изменен!", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(this@LoginActivity, "Кодовое слово неверное.", Toast.LENGTH_LONG).show()
                } else Toast.makeText(this@LoginActivity, "Номер не найден. Проверьте ввод.", Toast.LENGTH_SHORT).show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun saveLocalAndStart(name: String, phone: String, role: String) {
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("USER_NAME", name)
        editor.putString("USER_PHONE", phone)
        editor.putString("USER_ROLE", role)
        editor.putBoolean("IS_LOGGED_IN", true)
        editor.apply()

        Toast.makeText(this, "Вход выполнен! Ваша роль: $role", Toast.LENGTH_LONG).show()
        openMainActivity(phone)
    }

    private fun isValidPassword(password: String): Boolean {
        if (password.length < 6 || password.length > 12) return false
        val regex = "^[a-zA-Z0-9]+$"
        return password.matches(Regex(regex))
    }

    private fun isUserLoggedIn(): Boolean {
        return getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE).getBoolean("IS_LOGGED_IN", false)
    }

    private fun openMainActivity(phone: String?) {
        val intent = Intent(this, MainActivity::class.java)
        if (phone != null) {
            intent.putExtra("phone", phone)
        }
        startActivity(intent)
        finish()
    }
}