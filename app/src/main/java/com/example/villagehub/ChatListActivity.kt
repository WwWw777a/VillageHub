package com.example.villagehub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ChatListActivity : AppCompatActivity() {

    private val chatList = mutableListOf<ChatPreview>()
    private lateinit var adapter: ChatListAdapter
    private var myPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_chat_list)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Мои переписки"

        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        myPhone = sharedPref.getString("USER_PHONE", "") ?: ""

        val recycler = findViewById<RecyclerView>(R.id.recycler_chat_list)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = ChatListAdapter(
            chats = chatList,
            onOpenClick = { chatPreview ->
                // Открыть переписку
                val chatId = if (myPhone < chatPreview.phone) "${myPhone}_${chatPreview.phone}" else "${chatPreview.phone}_${myPhone}"
                val intent = Intent(this, PrivateChatActivity::class.java)
                intent.putExtra("CHAT_ID", chatId)
                intent.putExtra("OTHER_USER_PHONE", chatPreview.phone)
                startActivity(intent)
            },
            onDeleteClick = { chatPreview ->
                // Спросить перед удалением
                confirmDelete(chatPreview.phone)
            }
        )
        recycler.adapter = adapter

        loadChats()
    }

    private fun loadChats() {
        // Загружаем список из папки UserChats -> МойТелефон
        val ref = FirebaseDatabase.getInstance().getReference("UserChats").child(myPhone)

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatList.clear()
                for (snap in snapshot.children) {
                    val phone = snap.child("phone").getValue(String::class.java) ?: ""
                    val lastMsg = snap.child("lastMessage").getValue(String::class.java) ?: ""
                    val time = snap.child("timestamp").getValue(Long::class.java) ?: 0L

                    if (phone.isNotEmpty()) {
                        chatList.add(ChatPreview(phone, lastMsg, time))
                    }
                }
                // Сортировка: новые сверху
                chatList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun confirmDelete(otherPhone: String) {
        AlertDialog.Builder(this)
            .setTitle("Удалить переписку?")
            .setMessage("Эта переписка исчезнет из вашего списка.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteChat(otherPhone)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteChat(otherPhone: String) {
        // Удаляем запись только у себя (в UserChats/МойТелефон/ЕгоТелефон)
        FirebaseDatabase.getInstance().getReference("UserChats")
            .child(myPhone)
            .child(otherPhone)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Переписка удалена", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// Данные и Адаптер
data class ChatPreview(
    val phone: String,
    val lastMessage: String,
    val timestamp: Long
)

class ChatListAdapter(
    private val chats: List<ChatPreview>,
    private val onOpenClick: (ChatPreview) -> Unit,
    private val onDeleteClick: (ChatPreview) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val root: LinearLayout = v.findViewById(R.id.chat_item_root)
        val phone: TextView = v.findViewById(R.id.chat_user_phone)
        val msg: TextView = v.findViewById(R.id.chat_last_message)
        val btnDelete: ImageView = v.findViewById(R.id.btn_delete_chat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_list, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val chat = chats[position]
        holder.phone.text = chat.phone
        holder.msg.text = chat.lastMessage

        // Клик по тексту -> открыть чат
        holder.root.setOnClickListener { onOpenClick(chat) }

        // Клик по корзине -> удалить
        holder.btnDelete.setOnClickListener { onDeleteClick(chat) }
    }

    override fun getItemCount(): Int = chats.size
}