package com.example.villagehub

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout // Импорт контейнера
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

// --- ИМПОРТЫ ЯНДЕКС (БАННЕР) ---
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest

class IssuesFeedActivity : AppCompatActivity() {

    private val issuesList = mutableListOf<IssueItem>()
    private lateinit var adapter: IssuesAdapter
    private var myPhone: String = ""
    private var amIAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issues_feed)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_issues_feed)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Лента проблем"

        // Получаем свои данные
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        myPhone = sharedPref.getString("USER_PHONE", "") ?: ""
        val role = sharedPref.getString("USER_ROLE", "Житель")
        // Проверка: админ я или нет
        amIAdmin = (role == "АДМИН" || role == "admin" || role == "Admin")

        // Настройка списка
        val recycler = findViewById<RecyclerView>(R.id.recycler_issues_feed)
        recycler.layoutManager = LinearLayoutManager(this)

        // Передаем адаптеру инфу: кто мы
        adapter = IssuesAdapter(issuesList, myPhone, amIAdmin) { issue ->
            deleteIssue(issue)
        }
        recycler.adapter = adapter

        // Кнопка "Добавить"
        findViewById<CardView>(R.id.btn_add_new_issue).setOnClickListener {
            startActivity(Intent(this, IssuesActivity::class.java))
        }

        loadIssues()

        // --- ЗАПУСК БАННЕРА ---
        loadBannerAd()
    }

    // --- ФУНКЦИЯ ЗАГРУЗКИ БАННЕРА ---
    private fun loadBannerAd() {
        val bannerContainer = findViewById<FrameLayout>(R.id.banner_container)
        if (bannerContainer != null) {
            val banner = BannerAdView(this)

            // --- РЕАЛЬНЫЙ ID (ПРОБЛЕМЫ) ---
            banner.setAdUnitId("R-M-18551355-4")

            val displayMetrics = resources.displayMetrics
            val screenWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            banner.setAdSize(BannerAdSize.stickySize(this, screenWidth))

            bannerContainer.addView(banner)
            banner.loadAd(AdRequest.Builder().build())
        }
    }

    private fun loadIssues() {
        val ref = FirebaseDatabase.getInstance().getReference("Issues")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                issuesList.clear()
                for (snap in snapshot.children) {
                    val issue = snap.getValue(IssueItem::class.java)
                    if (issue != null) {
                        issuesList.add(issue)
                    }
                }
                // Сортировка: Новые сверху
                issuesList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun deleteIssue(issue: IssueItem) {
        AlertDialog.Builder(this)
            .setTitle("Удалить проблему?")
            .setMessage("Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                FirebaseDatabase.getInstance().getReference("Issues")
                    .child(issue.id)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// --- КЛАСС ДАННЫХ ---
data class IssueItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val phone: String = "",
    val image: String = "", // Base64 фото
    val timestamp: Long = 0,
    val status: String = ""
)

// --- АДАПТЕР ---
class IssuesAdapter(
    private val list: List<IssueItem>,
    private val myPhone: String,
    private val isAdmin: Boolean,
    private val onDeleteClick: (IssueItem) -> Unit
) : RecyclerView.Adapter<IssuesAdapter.IssueHolder>() {

    // Храним ID развернутых постов
    private val expandedIds = mutableSetOf<String>()

    class IssueHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tv_issue_title)
        val author: TextView = v.findViewById(R.id.tv_issue_author)
        val desc: TextView = v.findViewById(R.id.tv_issue_desc)
        val readMore: TextView = v.findViewById(R.id.tv_read_more_hint)
        val image: ImageView = v.findViewById(R.id.img_issue_photo)
        val btnDelete: ImageView = v.findViewById(R.id.btn_delete_issue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IssueHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_issue, parent, false)
        return IssueHolder(v)
    }

    override fun onBindViewHolder(holder: IssueHolder, position: Int) {
        val item = list[position]

        holder.title.text = item.title

        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        holder.author.text = "${item.author} • ${sdf.format(Date(item.timestamp))}"

        holder.desc.text = item.description

        // ЛОГИКА СВОРАЧИВАНИЯ ТЕКСТА
        val isExpanded = expandedIds.contains(item.id)
        if (isExpanded) {
            holder.desc.maxLines = Int.MAX_VALUE
            holder.readMore.text = "Свернуть ▲"
        } else {
            holder.desc.maxLines = 3
            holder.readMore.text = "Показать полностью ▼"
        }

        val toggleListener = View.OnClickListener {
            if (isExpanded) expandedIds.remove(item.id) else expandedIds.add(item.id)
            notifyItemChanged(position)
        }
        holder.desc.setOnClickListener(toggleListener)
        holder.readMore.setOnClickListener(toggleListener)

        // ФОТО
        if (item.image.isNotEmpty()) {
            holder.image.visibility = View.VISIBLE
            try {
                val imageBytes = Base64.decode(item.image, Base64.DEFAULT)
                Glide.with(holder.itemView.context).load(imageBytes).into(holder.image)
            } catch (e: Exception) {
                holder.image.visibility = View.GONE
            }
        } else {
            holder.image.visibility = View.GONE
        }

        // УДАЛЕНИЕ: Кнопка видна только автору или админу
        if (item.phone == myPhone || isAdmin) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDeleteClick(item) }
        } else {
            holder.btnDelete.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = list.size
}