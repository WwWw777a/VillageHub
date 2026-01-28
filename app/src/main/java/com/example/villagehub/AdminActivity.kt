package com.example.villagehub

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

// Модель
data class AdminUserItem(
    val phone: String = "",
    val name: String = "",
    val role: String = "",
    val regDate: Long = 0,
    val lastActive: Long = 0,
    val lastOnline: Long = 0,
    val isBanned: Boolean = false
)

class AdminActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var bannedRef: DatabaseReference
    private lateinit var rvUsers: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnGlobalMsg: Button

    private val allUsers = mutableListOf<AdminUserItem>()
    private val bannedPhones = mutableSetOf<String>()
    private val adapter = UsersAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        database = FirebaseDatabase.getInstance().getReference("Users")
        bannedRef = FirebaseDatabase.getInstance().getReference("BannedUsers")

        rvUsers = findViewById(R.id.rv_users_list)
        tvTotal = findViewById(R.id.tv_total_users)
        etSearch = findViewById(R.id.et_search_user)
        btnGlobalMsg = findViewById(R.id.btn_send_global_msg)

        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = adapter

        loadData()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnGlobalMsg.setOnClickListener { showGlobalMessageDialog() }
    }

    private fun loadData() {
        bannedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(bannedSnapshot: DataSnapshot) {
                bannedPhones.clear()
                for (snap in bannedSnapshot.children) {
                    val phone = snap.key ?: ""
                    bannedPhones.add(phone)
                }
                fetchUsersFromDB()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchUsersFromDB() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allUsers.clear()
                for (snap in snapshot.children) {
                    val phone = snap.key ?: ""
                    val name = snap.child("name").getValue(String::class.java) ?: "Без имени"
                    val role = snap.child("role").getValue(String::class.java) ?: "Житель"
                    val regDate = snap.child("regDate").getValue(Long::class.java) ?: 0L
                    val lastActive = snap.child("lastActive").getValue(Long::class.java) ?: 0L
                    val lastOnline = snap.child("lastOnline").getValue(Long::class.java) ?: 0L

                    val isBanned = bannedPhones.contains(phone)

                    allUsers.add(AdminUserItem(phone, name, role, regDate, lastActive, lastOnline, isBanned))
                }
                tvTotal.text = "Всего пользователей: ${allUsers.size}"
                filter(etSearch.text.toString())
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun filter(text: String) {
        val filteredList = mutableListOf<AdminUserItem>()
        for (user in allUsers) {
            if (user.name.contains(text, true) || user.phone.contains(text, true)) {
                filteredList.add(user)
            }
        }
        adapter.setList(filteredList)
    }

    private fun showGlobalMessageDialog() {
        val input = EditText(this)
        input.hint = "Введите текст объявления..."
        AlertDialog.Builder(this)
            .setTitle("📢 Отправить всем пуш")
            .setView(input)
            .setPositiveButton("Отправить") { _, _ ->
                val msg = input.text.toString()
                if (msg.isNotEmpty()) {
                    val alertRef = FirebaseDatabase.getInstance().getReference("SystemAlerts")
                    alertRef.setValue(mapOf("text" to msg, "time" to System.currentTimeMillis()))
                    Toast.makeText(this, "Уведомление отправлено!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null).show()
    }

    // --- АДАПТЕР ---
    inner class UsersAdapter : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {
        private var list = listOf<AdminUserItem>()

        fun setList(newList: List<AdminUserItem>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_user, parent, false)
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount() = list.size

        inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tv_admin_user_name)
            private val tvPhone: TextView = itemView.findViewById(R.id.tv_admin_user_phone)
            private val tvRole: TextView = itemView.findViewById(R.id.tv_admin_user_role)
            private val tvDates: TextView = itemView.findViewById(R.id.tv_admin_user_dates)

            fun bind(user: AdminUserItem) {
                tvName.text = user.name
                tvPhone.text = user.phone

                if (user.isBanned) {
                    tvRole.text = "⛔ ЗАБАНЕН"
                    tvRole.setTextColor(Color.RED)
                } else {
                    tvRole.text = user.role
                    when (user.role) {
                        "АДМИН" -> tvRole.setTextColor(Color.parseColor("#4CAF50"))
                        "МОДЕРАТОР" -> tvRole.setTextColor(Color.parseColor("#2196F3"))
                        else -> tvRole.setTextColor(Color.parseColor("#757575"))
                    }
                }

                val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
                val reg = if (user.regDate > 0L) sdf.format(Date(user.regDate)) else "нет данных"
                val realLastTime = if (user.lastOnline > user.lastActive) user.lastOnline else user.lastActive
                val lastSeenText = if (realLastTime > 0L) sdf.format(Date(realLastTime)) else "Никогда"

                tvDates.text = "Зареган: $reg\nБыл в сети: $lastSeenText"

                itemView.setOnClickListener { showUserOptions(user) }
            }
        }
    }

    private fun showUserOptions(user: AdminUserItem) {
        // Формируем меню
        val options = if (user.isBanned) {
            arrayOf("РАЗБАНИТЬ 🟢", "Сменить роль", "Отмена")
        } else {
            arrayOf("БАН НАВСЕГДА ⛔", "Сменить роль", "Отмена")
        }

        AlertDialog.Builder(this)
            .setTitle("Управление: ${user.name}")
            .setItems(options) { _, which ->
                // === ИСПРАВЛЕННАЯ ЛОГИКА ===
                // Смотрим на номер нажатой кнопки (which), а не на текст
                when (which) {
                    0 -> {
                        // Нажата ПЕРВАЯ кнопка (Бан или Разбан)
                        if (user.isBanned) {
                            unbanUserGlobal(user) // Если он в бане -> Разбанить
                        } else {
                            banUserGlobal(user) // Если не в бане -> Забанить
                        }
                    }
                    1 -> changeUserRole(user) // Нажата ВТОРАЯ кнопка
                }
            }.show()
    }

    private fun banUserGlobal(user: AdminUserItem) {
        AlertDialog.Builder(this).setTitle("БАН НАВСЕГДА").setMessage("Заблокировать ${user.name}?")
            .setPositiveButton("ЗАБАНИТЬ") { _, _ ->
                val banData = mapOf("timestamp" to -1L, "type" to "GLOBAL")
                bannedRef.child(user.phone).setValue(banData)
                Toast.makeText(this, "Пользователь забанен!", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Нет", null).show()
    }

    private fun unbanUserGlobal(user: AdminUserItem) {
        AlertDialog.Builder(this).setTitle("Разбанить").setMessage("Снять блокировку с ${user.name}?")
            .setPositiveButton("РАЗБАНИТЬ") { _, _ ->
                bannedRef.child(user.phone).removeValue()
                Toast.makeText(this, "Пользователь разбанен!", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Нет", null).show()
    }

    private fun changeUserRole(user: AdminUserItem) {
        val roles = arrayOf("Житель", "МОДЕРАТОР", "АДМИН", "Водитель")
        AlertDialog.Builder(this).setTitle("Назначить роль").setItems(roles) { _, which ->
            val newRole = roles[which]
            FirebaseDatabase.getInstance().getReference("Users").child(user.phone).child("role").setValue(newRole)
            Toast.makeText(this, "Роль изменена на $newRole", Toast.LENGTH_SHORT).show()
        }.show()
    }
}