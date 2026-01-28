package com.example.villagehub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream

class IssuesActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etDesc: EditText
    private lateinit var etAuthor: EditText
    private lateinit var btnPhoto: Button
    private lateinit var tvPhotoStatus: TextView
    private lateinit var imgPreview: ImageView
    private lateinit var btnSend: Button

    private var encodedImage: String = "" // Тут будет храниться фото

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issues)

        // Настройка Toolbar (кнопка назад)
        val toolbar = findViewById<Toolbar>(R.id.toolbar_issues)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Сообщить о проблеме"

        // Привязываем элементы
        etTitle = findViewById(R.id.et_issue_title)
        etDesc = findViewById(R.id.et_issue_desc)
        etAuthor = findViewById(R.id.et_issue_author)
        btnPhoto = findViewById(R.id.btn_issue_photo)
        tvPhotoStatus = findViewById(R.id.tv_photo_status)
        imgPreview = findViewById(R.id.img_issue_preview)
        btnSend = findViewById(R.id.btn_send_issue)

        // Автозаполнение имени, если есть в памяти
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val savedName = sharedPref.getString("USER_NAME", "")
        if (!savedName.isNullOrEmpty()) {
            etAuthor.setText(savedName)
        }

        // КНОПКА ФОТО
        btnPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 100)
        }

        // КНОПКА ОТПРАВИТЬ
        btnSend.setOnClickListener {
            sendIssueToFirebase()
        }
    }

    // Обработка выбора фото
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    // Показываем превью
                    imgPreview.setImageBitmap(bitmap)
                    imgPreview.visibility = View.VISIBLE
                    tvPhotoStatus.text = "Фото прикреплено!"

                    // Конвертируем в текст для отправки
                    encodedImage = encodeImage(bitmap)

                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка фото", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Функция сжатия фото
    private fun encodeImage(bitmap: Bitmap): String {
        val previewWidth = 800
        val previewHeight = bitmap.height * previewWidth / bitmap.width
        val resized = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false)
        val byteArrayOutputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        val bytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    // ОТПРАВКА В БАЗУ
    private fun sendIssueToFirebase() {
        val title = etTitle.text.toString().trim()
        val desc = etDesc.text.toString().trim()
        val author = etAuthor.text.toString().trim()

        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val phone = sharedPref.getString("USER_PHONE", "Неизвестно") ?: "Неизвестно"

        if (title.isEmpty() || desc.isEmpty() || author.isEmpty()) {
            Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
            return
        }

        // --- ИСПРАВЛЕНИЕ: БЛОКИРУЕМ КНОПКУ ---
        btnSend.isEnabled = false
        btnSend.text = "Отправка..."
        // --------------------------------------

        // Ссылка на базу "Issues" (Проблемы)
        val database = FirebaseDatabase.getInstance().getReference("Issues")
        val issueId = database.push().key ?: return

        // Создаем объект данных
        val issueMap = mapOf(
            "id" to issueId,
            "title" to title,
            "description" to desc,
            "author" to author,
            "phone" to phone,
            "image" to encodedImage,
            "timestamp" to System.currentTimeMillis(),
            "status" to "Новая"
        )

        // Отправляем
        database.child(issueId).setValue(issueMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Заявка отправлена!", Toast.LENGTH_LONG).show()
                finish() // Закрываем окно и возвращаемся назад
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка отправки. Проверьте интернет.", Toast.LENGTH_SHORT).show()
                // Если ошибка — возвращаем кнопку к жизни
                btnSend.isEnabled = true
                btnSend.text = "Отправить"
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}