package com.example.villagehub

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

class AddAdActivity : AppCompatActivity() {

    private lateinit var photosContainer: LinearLayout
    private lateinit var tvPhotoCount: TextView

    // Храним сразу готовые строки Base64
    private val finalImageStrings = ArrayList<String>()
    // Временный список для отображения в галерее (URI или String)
    private val tempDisplayImages = ArrayList<Any>()

    private var isEditMode = false
    private var editingAdId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ad)

        // --- 1. НАСТРОЙКА TOOLBAR (Синяя шапка) ---
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar_add_ad)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Показать стрелку
        supportActionBar?.title = "Новое объявление"      // Заголовок по умолчанию
        // ------------------------------------------

        val inputTitle: EditText = findViewById(R.id.input_title)
        val inputPrice: EditText = findViewById(R.id.input_price)
        val inputPhone: EditText = findViewById(R.id.input_phone)
        val inputDesc: EditText = findViewById(R.id.input_desc)
        val btnPublish: Button = findViewById(R.id.btn_publish)
        val btnSelectPhoto: Button = findViewById(R.id.btn_select_photo)
        val btnDelete: Button = findViewById(R.id.btn_delete_ad)
        // tvTitleScreen убрали, так как теперь заголовок в Toolbar

        photosContainer = findViewById(R.id.photos_container)
        tvPhotoCount = findViewById(R.id.tv_photo_count)

        val adToEdit = intent.getSerializableExtra("AD_EDIT") as? Ad
        if (adToEdit != null) {
            isEditMode = true
            editingAdId = adToEdit.id

            // Меняем заголовок в шапке
            supportActionBar?.title = "Редактирование"

            btnPublish.text = "Сохранить"
            btnDelete.visibility = View.VISIBLE

            inputTitle.setText(adToEdit.title)
            inputPrice.setText(adToEdit.price)
            inputPhone.setText(adToEdit.phone)
            inputDesc.setText(adToEdit.description)

            // Восстанавливаем фото из сохраненных строк
            finalImageStrings.addAll(adToEdit.imageUrls)
            tempDisplayImages.addAll(adToEdit.imageUrls)
            refreshPhotoGallery()
        } else {
            val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
            val myPhone = sharedPref.getString("USER_PHONE", "")
            if (!myPhone.isNullOrEmpty()) inputPhone.setText(myPhone)
        }

        btnSelectPhoto.setOnClickListener {
            if (finalImageStrings.size >= 5) {
                Toast.makeText(this, "Максимум 5 фото!", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                startActivityForResult(Intent.createChooser(intent, "Выберите фото"), 100)
            }
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удалить?")
                .setMessage("Действие необратимо.")
                .setPositiveButton("Да") { _, _ ->
                    if (editingAdId != null) {
                        FirebaseDatabase.getInstance().getReference("Ads")
                            .child(editingAdId!!).removeValue()
                        Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, BoardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Нет", null)
                .show()
        }

        btnPublish.setOnClickListener {
            val title = inputTitle.text.toString().trim()
            val price = inputPrice.text.toString().trim()
            val phone = inputPhone.text.toString().trim()
            val desc = inputDesc.text.toString().trim()

            if (title.isEmpty() || price.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Заполните основные поля!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveAdToDatabase(title, price, phone, desc)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val progress = ProgressDialog(this)
            progress.setMessage("Обработка фото...")
            progress.show()

            Thread {
                if (data?.clipData != null) {
                    val count = data.clipData!!.itemCount
                    for (i in 0 until count) {
                        if (finalImageStrings.size < 5) {
                            val uri = data.clipData!!.getItemAt(i).uri
                            processAndAddImage(uri)
                        }
                    }
                } else if (data?.data != null) {
                    if (finalImageStrings.size < 5) {
                        processAndAddImage(data.data!!)
                    }
                }

                runOnUiThread {
                    progress.dismiss()
                    refreshPhotoGallery()
                }
            }.start()
        }
    }

    // --- ФУНКЦИЯ СЖАТИЯ И ПРЕВРАЩЕНИЯ В ТЕКСТ ---
    private fun processAndAddImage(uri: Uri) {
        try {
            val imageStream: InputStream? = contentResolver.openInputStream(uri)
            val selectedImage = BitmapFactory.decodeStream(imageStream)

            // 1. Сжимаем размер (ширина не более 800 пикселей)
            val scaledBitmap = getResizedBitmap(selectedImage, 800)

            // 2. Сжимаем качество и превращаем в байты
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos) // Качество 60%
            val imageBytes = baos.toByteArray()

            // 3. Превращаем в строку Base64
            val imageString = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            finalImageStrings.add(imageString)
            tempDisplayImages.add(uri) // Для отображения покажем локальную, так быстрее

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    private fun refreshPhotoGallery() {
        photosContainer.removeAllViews()
        tvPhotoCount.text = "Выбрано: ${finalImageStrings.size} из 5"

        for ((index, item) in tempDisplayImages.withIndex()) {
            val frame = FrameLayout(this)
            val params = LinearLayout.LayoutParams(200, 200)
            params.setMargins(8, 0, 8, 0)
            frame.layoutParams = params

            val imgView = ImageView(this)
            imgView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            imgView.scaleType = ImageView.ScaleType.CENTER_CROP

            // Если это Uri (новое фото)
            if (item is Uri) {
                Glide.with(this).load(item).into(imgView)
            }
            // Если это String (старое фото из базы)
            else if (item is String) {
                val bytes = Base64.decode(item, Base64.DEFAULT)
                Glide.with(this).load(bytes).into(imgView)
            }

            val closeBtn = ImageView(this)
            val closeParams = FrameLayout.LayoutParams(50, 50)
            closeParams.setMargins(0, 8, 8, 0)
            closeParams.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            closeBtn.layoutParams = closeParams
            closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            closeBtn.setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            closeBtn.setPadding(8,8,8,8)
            closeBtn.setColorFilter(android.graphics.Color.WHITE)

            closeBtn.setOnClickListener {
                finalImageStrings.removeAt(index)
                tempDisplayImages.removeAt(index)
                refreshPhotoGallery()
            }

            frame.addView(imgView)
            frame.addView(closeBtn)
            photosContainer.addView(frame)
        }
    }

    private fun saveAdToDatabase(title: String, price: String, phone: String, desc: String) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Публикация...")
        progressDialog.show()

        val database = FirebaseDatabase.getInstance().getReference("Ads")
        val adId = if (isEditMode) editingAdId!! else database.push().key ?: return

        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val authorName = sharedPref.getString("USER_NAME", "Житель") ?: "Житель"

        val adData = Ad(
            id = adId,
            title = title,
            price = price,
            description = desc,
            phone = phone,
            author = authorName,
            imageUrls = finalImageStrings, // Сохраняем ТЕКСТ картинок
            timestamp = System.currentTimeMillis()
        )

        database.child(adId).setValue(adData)
            .addOnSuccessListener {
                progressDialog.dismiss()
                val msg = if (isEditMode) "Сохранено!" else "Опубликовано!"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                val intent = Intent(this, BoardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Ошибка: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- 2. ОБРАБОТКА НАЖАТИЯ СТРЕЛКИ НАЗАД ---
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}