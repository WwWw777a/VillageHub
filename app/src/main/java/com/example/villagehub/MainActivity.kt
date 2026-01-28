package com.example.villagehub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

// --- ИМПОРТЫ ЯНДЕКС РЕКЛАМЫ ---
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.MobileAds

// --- НОВЫЙ ИМПОРТ (Для Токена) ---
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    // === НОМЕР АДМИНА ДЛЯ СВЯЗИ ===
    private val ADMIN_PHONE = "+79002712293"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- 1. ЛОГИКА СОХРАНЕНИЯ НОМЕРА (ВАЖНО!) ---
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val phoneFromLogin = intent.getStringExtra("phone")

        // Если пришли с экрана входа — сохраняем номер
        if (!phoneFromLogin.isNullOrEmpty()) {
            sharedPref.edit().putString("USER_PHONE", phoneFromLogin).apply()
        }

        // Проверяем, есть ли номер в памяти
        val savedPhone = sharedPref.getString("USER_PHONE", null)
        if (savedPhone.isNullOrEmpty()) {
            // Если номера нет — выкидываем на логин
            Toast.makeText(this, "Ошибка авторизации. Войдите заново.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        // ---------------------------------------------

        // --- 2. ДИНАМИЧЕСКОЕ ПРИВЕТСТВИЕ ---
        setupDynamicGreeting(sharedPref)

        // --- 3. НАСТРОЙКА КНОПОК ---
        setupButton(R.id.card_board, BoardActivity::class.java)

        // БЫЛ АВТОБУС -> СТАЛО ТАКСИ
        setupButton(R.id.card_taxi, TaxiActivity::class.java)

        // Открываем Ленту (Feed), а не Форму
        setupButton(R.id.card_issues, IssuesFeedActivity::class.java)

        setupButton(R.id.card_chat, ChatActivity::class.java)

        // --- НОВАЯ КНОПКА: СВЯЗЬ С АДМИНОМ ---
        findViewById<View>(R.id.card_contact_admin).setOnClickListener {
            // Проверка, не пытается ли Админ написать сам себе
            if (savedPhone == ADMIN_PHONE) {
                Toast.makeText(this, "Это ваш номер Админа", Toast.LENGTH_SHORT).show()
            } else {
                openPrivateChatWithAdmin(savedPhone!!, ADMIN_PHONE)
            }
        }

        // --- КНОПКА: КОНВЕРТИК (ВАШИ СООБЩЕНИЯ) ---
        findViewById<View>(R.id.btn_open_messages).setOnClickListener {
            updateLastOnline()
            // Сбрасываем уведомление при входе
            resetNotification(savedPhone!!)
            // Открываем список переписок
            startActivity(Intent(this, ChatListActivity::class.java))
        }

        // --- 4. АДМИН ПАНЕЛЬ ---
        val btnAdmin = findViewById<ImageView>(R.id.btn_admin_panel)
        val role = sharedPref.getString("USER_ROLE", "Житель")

        if (role == "АДМИН" || role == "admin" || role == "Admin") {
            btnAdmin.visibility = View.VISIBLE
            btnAdmin.setOnClickListener {
                updateLastOnline()
                startActivity(Intent(this, AdminActivity::class.java))
            }
        } else {
            btnAdmin.visibility = View.GONE
        }

        listenForSystemAlerts()

        // --- 5. ЗАПУСК ПРОВЕРКИ УВЕДОМЛЕНИЙ ---
        checkIncomingMessages(savedPhone!!)

        // --- 6. ЗАПУСК РЕКЛАМЫ (YANDEX ADS) ---
        MobileAds.initialize(this) {
            // Инициализация прошла успешно
        }
        loadBannerAd()

        // --- 7. ЗАПРОС РАЗРЕШЕНИЯ НА УВЕДОМЛЕНИЯ (НОВОЕ, Android 13+) ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // --- 8. СОХРАНЕНИЕ ТОКЕНА В БАЗУ (ВАЖНО!) ---
        saveUserToken(savedPhone!!)
    }

    // --- ФУНКЦИЯ СОХРАНЕНИЯ ТОКЕНА ---
    private fun saveUserToken(phone: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@addOnCompleteListener
            }
            // Получаем токен
            val token = task.result

            // Сохраняем в базу: Users -> +7900... -> token = "d3f..."
            FirebaseDatabase.getInstance().getReference("Users")
                .child(phone)
                .child("token")
                .setValue(token)
        }
    }
    // ------------------------------------

    // --- ФУНКЦИЯ ЗАГРУЗКИ БАННЕРА ---
    private fun loadBannerAd() {
        val bannerContainer = findViewById<FrameLayout>(R.id.banner_container)

        // Создаем баннер программно
        val banner = BannerAdView(this)

        // --- РЕАЛЬНЫЙ ID (ГЛАВНАЯ) ---
        banner.setAdUnitId("R-M-18551355-1")

        // 2. Рассчитываем размер (Sticky - прилипающий к низу)
        banner.setAdSize(getAdSize())

        // 3. Добавляем в наш контейнер на экране
        bannerContainer.addView(banner)

        // 4. Загружаем рекламу
        val adRequest = AdRequest.Builder().build()
        banner.loadAd(adRequest)
    }

    private fun getAdSize(): BannerAdSize {
        // Вычисляем ширину экрана для баннера
        val displayMetrics = resources.displayMetrics
        val screenWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return BannerAdSize.stickySize(this, screenWidth)
    }
    // ------------------------------------------

    // --- ФУНКЦИЯ ОТКРЫТИЯ ЧАТА С АДМИНОМ ---
    private fun openPrivateChatWithAdmin(myPhone: String, adminPhone: String) {
        // Генерируем ID чата (сортируем номера, чтобы ID был одинаковым у обоих)
        val chatId = if (myPhone < adminPhone) {
            "${myPhone}_${adminPhone}"
        } else {
            "${adminPhone}_${myPhone}"
        }

        val intent = Intent(this, PrivateChatActivity::class.java)
        intent.putExtra("CHAT_ID", chatId)
        intent.putExtra("OTHER_USER_PHONE", adminPhone)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateLastOnline()
        // Обновляем приветствие при возврате
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        setupDynamicGreeting(sharedPref)

        // Проверяем уведомления при возврате на экран
        val myPhone = sharedPref.getString("USER_PHONE", "") ?: ""
        if (myPhone.isNotEmpty()) checkIncomingMessages(myPhone)
    }

    // --- ФУНКЦИЯ ПРОВЕРКИ НОВЫХ СООБЩЕНИЙ ---
    private fun checkIncomingMessages(myPhone: String) {
        val badge = findViewById<CardView>(R.id.badge_new_msg)
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(myPhone)

        // Слушаем поле "hasNewMessage" в базе данных
        userRef.child("hasNewMessage").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val hasNew = snapshot.getValue(Boolean::class.java) ?: false
                if (hasNew) {
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- СБРОС УВЕДОМЛЕНИЯ ---
    private fun resetNotification(myPhone: String) {
        FirebaseDatabase.getInstance().getReference("Users")
            .child(myPhone).child("hasNewMessage").setValue(false)
    }

    // --- ФУНКЦИЯ ПРИВЕТСТВИЯ ---
    private fun setupDynamicGreeting(sharedPref: android.content.SharedPreferences) {
        val tvGreeting = findViewById<TextView>(R.id.tv_greeting)

        // Берем имя пользователя (или "Житель", если имени нет)
        val userName = sharedPref.getString("USER_NAME", "Житель") ?: "Житель"

        // Смотрим на часы
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // Выбираем фразу
        val greeting = when (hour) {
            in 6..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..23 -> "Добрый вечер"
            else -> "Доброй ночи"
        }

        // Ставим текст в заголовок
        tvGreeting.text = "$greeting, $userName!"
    }

    // Вспомогательная функция для кнопок
    private fun setupButton(cardId: Int, activityClass: Class<*>) {
        findViewById<CardView>(cardId).setOnClickListener {
            updateLastOnline()
            startActivity(Intent(this, activityClass))
        }
    }

    // --- ОБНОВЛЕНИЕ ВРЕМЕНИ ---
    private fun updateLastOnline() {
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val userPhone = sharedPref.getString("USER_PHONE", "")

        if (!userPhone.isNullOrEmpty()) {
            FirebaseDatabase.getInstance().getReference("Users")
                .child(userPhone)
                .child("lastOnline")
                .setValue(System.currentTimeMillis())
        }
    }

    // --- УВЕДОМЛЕНИЯ ОТ СИСТЕМЫ ---
    private fun listenForSystemAlerts() {
        val alertRef = FirebaseDatabase.getInstance().getReference("SystemAlerts")
        alertRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val text = snapshot.child("text").getValue(String::class.java) ?: ""
                    val time = snapshot.child("time").getValue(Long::class.java) ?: 0L
                    if (text.isNotEmpty() && (System.currentTimeMillis() - time) < 10 * 60 * 1000) {
                        showSystemDialog(text)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showSystemDialog(text: String) {
        if (!isFinishing && !isDestroyed) {
            AlertDialog.Builder(this)
                .setTitle("📢 ВНИМАНИЕ!")
                .setMessage(text)
                .setPositiveButton("Понятно") { _, _ -> }
                .show()
        }
    }
}