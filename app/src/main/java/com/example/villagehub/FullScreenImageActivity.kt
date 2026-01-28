package com.example.villagehub

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class FullScreenImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        val viewPager: ViewPager2 = findViewById(R.id.view_pager)
        val btnClose: ImageView = findViewById(R.id.btn_close_full)

        // Получаем список картинок из DataHolder
        val images = DataHolder.imagesList
        // Получаем номер картинки, на которую нажали (по умолчанию 0)
        val startPosition = intent.getIntExtra("START_POSITION", 0)

        if (images != null && images.isNotEmpty()) {
            val adapter = ImageSliderAdapter(images)
            viewPager.adapter = adapter
            // Перелистываем сразу на нужную картинку (false = без анимации прокрутки)
            viewPager.setCurrentItem(startPosition, false)
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            DataHolder.imagesList = null
        }
    }
}