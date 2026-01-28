package com.example.villagehub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout // Импорт контейнера
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

// --- ИМПОРТЫ ЯНДЕКС (БАННЕР) ---
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest

class TaxiActivity : AppCompatActivity() {

    private val taxiList = mutableListOf<TaxiItem>()
    private lateinit var adapter: TaxiAdapter
    private lateinit var database: DatabaseReference
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_taxi)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_taxi)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        // Проверка прав
        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        val role = sharedPref.getString("USER_ROLE", "Житель")
        isAdmin = (role == "АДМИН" || role == "admin" || role == "Admin")

        // Кнопка добавления (только для админа)
        val btnAdd = findViewById<ImageView>(R.id.btn_add_taxi)
        if (isAdmin) {
            btnAdd.visibility = View.VISIBLE
            btnAdd.setOnClickListener { showAddTaxiDialog() }
        } else {
            btnAdd.visibility = View.GONE
        }

        // Настройка списка
        val recycler = findViewById<RecyclerView>(R.id.recycler_taxi)
        recycler.layoutManager = LinearLayoutManager(this)

        // Создаем адаптер
        adapter = TaxiAdapter(taxiList,
            onItemClick = { taxi -> showCallDialog(taxi) },
            onItemLongClick = { taxi -> if (isAdmin) showDeleteDialog(taxi) }
        )
        recycler.adapter = adapter

        // Загрузка данных
        database = FirebaseDatabase.getInstance().getReference("Taxi")
        loadTaxi()

        // --- ЗАПУСК БАННЕРА ---
        loadBannerAd()
    }

    // --- ФУНКЦИЯ ЗАГРУЗКИ БАННЕРА ---
    private fun loadBannerAd() {
        val bannerContainer = findViewById<FrameLayout>(R.id.banner_container)
        if (bannerContainer != null) {
            val banner = BannerAdView(this)

            // --- РЕАЛЬНЫЙ ID (ТАКСИ) ---
            banner.setAdUnitId("R-M-18551355-3")

            val displayMetrics = resources.displayMetrics
            val screenWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            banner.setAdSize(BannerAdSize.stickySize(this, screenWidth))

            bannerContainer.addView(banner)
            banner.loadAd(AdRequest.Builder().build())
        }
    }

    private fun loadTaxi() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                taxiList.clear()
                for (snap in snapshot.children) {
                    val taxi = snap.getValue(TaxiItem::class.java)
                    if (taxi != null) {
                        taxi.id = snap.key ?: ""
                        taxiList.add(taxi)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddTaxiDialog() {
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val inputName = EditText(this)
        inputName.hint = "Название (Например: Дядя Ваня)"
        layout.addView(inputName)

        val inputPhone = EditText(this)
        inputPhone.hint = "Номер телефона (+7...)"
        inputPhone.inputType = android.text.InputType.TYPE_CLASS_PHONE
        layout.addView(inputPhone)

        AlertDialog.Builder(this)
            .setTitle("Добавить такси")
            .setView(layout)
            .setPositiveButton("Добавить") { _, _ ->
                val name = inputName.text.toString()
                val phone = inputPhone.text.toString()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    val id = database.push().key ?: return@setPositiveButton
                    val newTaxi = TaxiItem(id, name, phone)
                    database.child(id).setValue(newTaxi)
                    Toast.makeText(this, "Такси добавлено!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCallDialog(taxi: TaxiItem) {
        AlertDialog.Builder(this)
            .setTitle("Позвонить в такси?")
            .setMessage("Вы хотите позвонить: ${taxi.name}?\nНомер: ${taxi.phone}")
            .setPositiveButton("ДА") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${taxi.phone}")
                startActivity(intent)
            }
            .setNegativeButton("НЕТ", null)
            .show()
    }

    private fun showDeleteDialog(taxi: TaxiItem) {
        AlertDialog.Builder(this)
            .setTitle("Удалить такси?")
            .setMessage("Удалить ${taxi.name} из списка?")
            .setPositiveButton("Удалить") { _, _ ->
                database.child(taxi.id).removeValue()
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// --- НИЖЕ ИДУТ КЛАССЫ, КОТОРЫЕ ОБЯЗАТЕЛЬНО ДОЛЖНЫ БЫТЬ В ФАЙЛЕ ---

data class TaxiItem(
    var id: String = "",
    val name: String = "",
    val phone: String = ""
)

class TaxiAdapter(
    private val list: List<TaxiItem>,
    private val onItemClick: (TaxiItem) -> Unit,
    private val onItemLongClick: (TaxiItem) -> Unit
) : RecyclerView.Adapter<TaxiAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tv_taxi_name)
        val phone: TextView = v.findViewById(R.id.tv_taxi_phone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_taxi, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val taxi = list[position]
        holder.name.text = taxi.name
        holder.phone.text = taxi.phone

        holder.itemView.setOnClickListener { onItemClick(taxi) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(taxi)
            true
        }
    }

    override fun getItemCount(): Int = list.size
}