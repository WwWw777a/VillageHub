package com.example.villagehub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod // <--- ДОБАВИЛ ДЛЯ ПРОКРУТКИ ТЕКСТА
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*

// --- ИМПОРТЫ YANDEX ADS ---
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader

// --- ИМПОРТЫ ДЛЯ БАННЕРА ---
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest

class BoardActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView

    private lateinit var adapter: AdsAdapter
    private val adsList = mutableListOf<Ad>()

    // --- ПЕРЕМЕННЫЕ ДЛЯ РЕКЛАМЫ (YANDEX) ---
    private var rewardedAd: RewardedAd? = null
    private var rewardedAdLoader: RewardedAdLoader? = null
    private var adWasShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_board)

        // 1. Настройка Toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Главное меню"

        // 2. Инициализация списка и надписи
        recyclerView = findViewById(R.id.recycler_view_board)
        tvEmptyState = findViewById(R.id.tv_empty_board)

        recyclerView.layoutManager = GridLayoutManager(this, 2)

        adapter = AdsAdapter(adsList) { selectedAd ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("AD_DATA", selectedAd)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // 3. Загрузка данных из Интернета
        database = FirebaseDatabase.getInstance().getReference("Ads")
        loadAds()

        // --- 4. НАСТРОЙКА ЗАГРУЗЧИКА ВИДЕО-РЕКЛАМЫ ---
        rewardedAdLoader = RewardedAdLoader(this).apply {
            setAdLoadListener(object : RewardedAdLoadListener {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(error: AdRequestError) {
                    rewardedAd = null
                }
            })
        }
        loadRewardedAd()

        // 5. Кнопка "Добавить объявление" (+)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fab_add_ad)
        fabAdd.setOnClickListener {
            showConfirmationDialog()
        }

        // 6. КНОПКА "ПРАВИЛА" (НОВОЕ)
        val btnRules = findViewById<Button>(R.id.btn_rules)
        btnRules.setOnClickListener {
            loadAndShowRules()
        }

        // --- 7. ЗАГРУЗКА БАННЕРА ВНИЗУ ---
        loadBannerAd()
    }

    // --- ЛОГИКА ПРАВИЛ ---
    private fun loadAndShowRules() {
        // 1. Проверяем роль пользователя (из сохраненных настроек)
        val prefs = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val role = prefs.getString("USER_ROLE", "Житель") ?: "Житель"
        val isAdmin = role == "АДМИН" || role == "МОДЕРАТОР"

        // 2. Загружаем текст правил из Firebase
        val rulesRef = FirebaseDatabase.getInstance().getReference("Rules").child("board_rules")

        // Показываем "Загрузка..." пока ждем ответ
        Toast.makeText(this, "Загрузка правил...", Toast.LENGTH_SHORT).show()

        rulesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rulesText = snapshot.getValue(String::class.java) ?: "Правила еще не добавлены."
                showRulesDialog(rulesText, isAdmin, rulesRef)
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BoardActivity, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showRulesDialog(text: String, isAdmin: Boolean, ref: DatabaseReference) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Правила доски объявлений")

        if (isAdmin) {
            // --- РЕЖИМ АДМИНА (РЕДАКТИРОВАНИЕ) ---
            val input = EditText(this)
            input.setText(text)
            input.gravity = Gravity.TOP or Gravity.START

            // !!! ИСПРАВЛЕНИЕ: Ограничиваем высоту и включаем прокрутку !!!
            input.maxLines = 10
            input.isVerticalScrollBarEnabled = true
            input.movementMethod = ScrollingMovementMethod.getInstance()
            // -------------------------------------------------------------

            input.setBackgroundResource(android.R.drawable.edit_text)
            input.setPadding(40, 40, 40, 40)

            builder.setView(input)
            builder.setMessage("Вы АДМИН. Вы можете изменить текст правил:")

            builder.setPositiveButton("СОХРАНИТЬ") { _, _ ->
                val newText = input.text.toString()
                ref.setValue(newText)
                Toast.makeText(this, "Правила обновлены!", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Отмена", null)

        } else {
            // --- РЕЖИМ ОБЫЧНОГО ЖИТЕЛЯ (ТОЛЬКО ЧТЕНИЕ) ---
            val messageView = TextView(this)
            messageView.text = text
            messageView.textSize = 16f
            messageView.setPadding(50, 30, 50, 30)
            messageView.setTextColor(resources.getColor(android.R.color.black))

            // !!! ИСПРАВЛЕНИЕ: Чтобы длинный текст можно было листать и у жителей !!!
            messageView.maxLines = 15
            messageView.isVerticalScrollBarEnabled = true
            messageView.movementMethod = ScrollingMovementMethod.getInstance()
            // ----------------------------------------------------------------------

            builder.setView(messageView)
            builder.setPositiveButton("Понятно") { dialog, _ ->
                dialog.dismiss()
            }
        }

        builder.show()
    }
    // ---------------------

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Новое объявление")
            .setMessage("Размещение объявления бесплатно, но для поддержки приложения нужно посмотреть короткую рекламу. Продолжить?")
            .setPositiveButton("Смотреть") { _, _ ->
                if (rewardedAd != null) {
                    showAd()
                } else {
                    Toast.makeText(this, "Реклама загружается... Подождите пару секунд и нажмите снова", Toast.LENGTH_SHORT).show()
                    loadRewardedAd()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadAds() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                adsList.clear()
                for (snap in snapshot.children) {
                    val ad = snap.getValue(Ad::class.java)
                    if (ad != null) {
                        adsList.add(0, ad)
                    }
                }

                if (adsList.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadRewardedAd() {
        val adUnitId = "R-M-18551355-5"
        val adRequestConfiguration = AdRequestConfiguration.Builder(adUnitId).build()
        rewardedAdLoader?.loadAd(adRequestConfiguration)
    }

    private fun showAd() {
        adWasShown = false
        rewardedAd?.setAdEventListener(object : RewardedAdEventListener {
            override fun onAdShown() {}
            override fun onAdFailedToShow(error: AdError) {
                rewardedAd = null
                openAddAdScreen()
                loadRewardedAd()
            }
            override fun onAdDismissed() {
                rewardedAd = null
                if (adWasShown) {
                    openAddAdScreen()
                } else {
                    Toast.makeText(this@BoardActivity, "Нужно посмотреть рекламу до конца!", Toast.LENGTH_SHORT).show()
                }
                loadRewardedAd()
            }
            override fun onAdClicked() {}
            override fun onAdImpression(data: ImpressionData?) {}
            override fun onRewarded(reward: Reward) {
                adWasShown = true
            }
        })
        rewardedAd?.show(this)
    }

    private fun loadBannerAd() {
        val bannerContainer = findViewById<FrameLayout>(R.id.banner_container)
        if (bannerContainer != null) {
            val banner = BannerAdView(this)
            banner.setAdUnitId("R-M-18551355-2")
            val displayMetrics = resources.displayMetrics
            val screenWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            banner.setAdSize(BannerAdSize.stickySize(this, screenWidth))
            bannerContainer.addView(banner)
            banner.loadAd(AdRequest.Builder().build())
        }
    }

    private fun openAddAdScreen() {
        startActivity(Intent(this, AddAdActivity::class.java))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        rewardedAdLoader = null
        rewardedAd = null
    }
}