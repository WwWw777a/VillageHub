package com.example.villagehub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class DetailActivity : AppCompatActivity() {

    // Запасной вариант (Супер-админ по жесткому номеру)
    private val ADMIN_PHONE = "+79002712293"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_detail)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val ad = intent.getSerializableExtra("AD_DATA") as? Ad
        if (ad == null) {
            finish()
            return
        }

        val photosContainer = findViewById<LinearLayout>(R.id.detail_photos_container)
        val tvTitle = findViewById<TextView>(R.id.detail_title)
        val tvPrice = findViewById<TextView>(R.id.detail_price)
        val tvDesc = findViewById<TextView>(R.id.detail_desc)
        val tvInfo = findViewById<TextView>(R.id.detail_info)
        val btnCall = findViewById<Button>(R.id.btn_call)
        val btnMessage = findViewById<Button>(R.id.btn_message)
        val btnEdit = findViewById<ImageView>(R.id.btn_edit_ad)

        tvTitle.text = ad.title
        tvPrice.text = "${ad.price} ₽"
        tvDesc.text = ad.description

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(ad.timestamp))
        tvInfo.text = "Продавец: ${ad.author} • Опубликовано: $dateStr"

        // --- 1. ЗАГРУЗКА ФОТО ---
        if (ad.imageUrls.isNotEmpty()) {
            for ((index, imageString) in ad.imageUrls.withIndex()) {
                val imgView = ImageView(this)
                val params = LinearLayout.LayoutParams(
                    resources.displayMetrics.widthPixels,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                imgView.layoutParams = params
                imgView.scaleType = ImageView.ScaleType.CENTER_CROP

                try {
                    val imageBytes = Base64.decode(imageString, Base64.DEFAULT)
                    Glide.with(this).load(imageBytes).into(imgView)

                    imgView.setOnClickListener {
                        DataHolder.imagesList = ad.imageUrls
                        val fullScreenIntent = Intent(this, FullScreenImageActivity::class.java)
                        fullScreenIntent.putExtra("START_POSITION", index)
                        startActivity(fullScreenIntent)
                    }
                } catch (e: Exception) {
                    imgView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
                photosContainer.addView(imgView)
            }
        } else {
            val placeholder = findViewById<ImageView>(R.id.detail_image_placeholder)
            placeholder.visibility = View.VISIBLE
            val params = LinearLayout.LayoutParams(
                resources.displayMetrics.widthPixels,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            placeholder.layoutParams = params
        }

        // --- 2. ПРОВЕРКА ПРАВ (С ЗАЩИТОЙ АДМИНА ОТ МОДЕРАТОРА) ---
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val myPhone = sharedPref.getString("USER_PHONE", "") ?: ""

        // Функция для показа кнопки
        fun showEditButton() {
            btnEdit.visibility = View.VISIBLE
            btnEdit.setOnClickListener {
                val editIntent = Intent(this@DetailActivity, AddAdActivity::class.java)
                editIntent.putExtra("AD_EDIT", ad)
                startActivity(editIntent)
            }
        }

        // ШАГ 1: Если это автор или жестко прописанный админ - разрешаем сразу
        if (myPhone == ad.phone || myPhone == ADMIN_PHONE) {
            showEditButton()
        }
        // ШАГ 2: Проверка роли в базе данных
        else if (myPhone.isNotEmpty()) {
            val userRef = FirebaseDatabase.getInstance().getReference("Users").child(myPhone)

            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val myRole = snapshot.child("role").getValue(String::class.java)

                        // Если я АДМИН -> могу редактировать всё
                        if (myRole == "admin" || myRole == "АДМИН") {
                            showEditButton()
                        }
                        // Если я МОДЕРАТОР -> нужно проверить, не является ли автор Админом
                        else if (myRole == "moderator" || myRole == "МОДЕРАТОР") {
                            checkAuthorRoleAndShowButton(ad.phone) {
                                showEditButton()
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        btnCall.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${ad.phone}")
            startActivity(intent)
        }

        // --- ЛОГИКА КНОПКИ "НАПИСАТЬ" ---
        btnMessage.setOnClickListener {
            val sellerPhone = ad.phone

            if (myPhone.isEmpty()) {
                Toast.makeText(this, "Сначала войдите в приложение!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (myPhone == sellerPhone) {
                Toast.makeText(this, "Это ваше объявление :)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Генерируем ID чата
            val chatId = if (myPhone < sellerPhone) {
                "${myPhone}_${sellerPhone}"
            } else {
                "${sellerPhone}_${myPhone}"
            }

            val chatIntent = Intent(this, PrivateChatActivity::class.java)
            chatIntent.putExtra("CHAT_ID", chatId)
            chatIntent.putExtra("OTHER_USER_PHONE", sellerPhone)
            startActivity(chatIntent)
        }
    }

    // Вспомогательная функция: Проверяет роль АВТОРА объявления
    // Если автор НЕ админ -> выполняет действие (показывает кнопку)
    private fun checkAuthorRoleAndShowButton(authorPhone: String, onAllow: () -> Unit) {
        // Если автор - жестко заданный супер-админ, сразу запрещаем
        if (authorPhone == ADMIN_PHONE) return

        val authorRef = FirebaseDatabase.getInstance().getReference("Users").child(authorPhone)
        authorRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val authorRole = snapshot.child("role").getValue(String::class.java) ?: "Житель"

                // Если автор объявления - АДМИН, то модератор НЕ получает доступ
                val isAuthorAdmin = authorRole == "admin" || authorRole == "АДМИН"

                if (!isAuthorAdmin) {
                    onAllow() // Автор простой житель или другой модератор -> разрешаем
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}