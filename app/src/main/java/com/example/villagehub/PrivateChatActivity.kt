package com.example.villagehub

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
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

class PrivateChatActivity : AppCompatActivity() {

    private lateinit var databaseRef: DatabaseReference
    private lateinit var adapter: PrivateChatAdapter
    private val messageList = mutableListOf<PrivateMessage>()

    private var chatId: String = ""
    private var myPhone: String = ""
    private var otherUserPhone: String = ""
    private var myName: String = "Житель"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 1. Получаем данные
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        otherUserPhone = intent.getStringExtra("OTHER_USER_PHONE") ?: ""

        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        myPhone = sharedPref.getString("USER_PHONE", "") ?: ""
        myName = sharedPref.getString("USER_NAME", "Житель") ?: "Житель"

        if (chatId.isEmpty() || myPhone.isEmpty()) {
            Toast.makeText(this, "Ошибка чата", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar_chat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Чат"
        loadOtherUserName()

        // 3. UI
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_chat)
        val inputField = findViewById<EditText>(R.id.chat_input)
        val sendButton = findViewById<ImageButton>(R.id.btn_send_chat)

        findViewById<View>(R.id.layout_reply_preview)?.visibility = View.GONE

        adapter = PrivateChatAdapter(messageList, myPhone)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        // 4. Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference("PrivateChats").child(chatId)
        listenForMessages(recyclerView)

        // 5. Отправка
        sendButton.setOnClickListener {
            val text = inputField.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                inputField.text.clear()
            }
        }
    }

    // --- НАЧАЛО БЛОКА УДАЛЕНИЯ ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val item = menu?.add(0, 1, 0, "Очистить чат")
        item?.setIcon(android.R.drawable.ic_menu_delete)
        item?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            showClearChatDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showClearChatDialog() {
        AlertDialog.Builder(this)
            .setTitle("Удалить переписку?")
            .setMessage("Все сообщения будут удалены навсегда.")
            .setPositiveButton("Удалить") { _, _ ->
                clearChat()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearChat() {
        databaseRef.removeValue()
        FirebaseDatabase.getInstance().getReference("UserChats")
            .child(myPhone).child(otherUserPhone).removeValue()
        Toast.makeText(this, "Чат очищен", Toast.LENGTH_SHORT).show()
        finish()
    }
    // --- КОНЕЦ БЛОКА УДАЛЕНИЯ ---

    private fun listenForMessages(recyclerView: RecyclerView) {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (postSnapshot in snapshot.children) {
                    val msg = postSnapshot.getValue(PrivateMessage::class.java)
                    if (msg != null) {
                        msg.id = postSnapshot.key ?: ""

                        if (msg.senderId != myPhone && !msg.isRead) {
                            databaseRef.child(msg.id).child("isRead").setValue(true)
                            msg.isRead = true
                        }
                        messageList.add(msg)
                    }
                }
                adapter.notifyDataSetChanged()
                if (messageList.isNotEmpty()) {
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun sendMessage(text: String) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = sdf.format(Date())
        val timestamp = System.currentTimeMillis()
        val messageId = databaseRef.push().key ?: return

        val newMessage = PrivateMessage(
            id = messageId,
            senderId = myPhone,
            text = text,
            time = currentTime,
            timestamp = timestamp,
            isRead = false
        )

        // 1. Сохраняем сообщение в базу
        databaseRef.child(messageId).setValue(newMessage)

        // 2. Ставим флажок hasNewMessage (Красная точка внутри приложения)
        notifyRecipientInternal()

        // 3. Обновляем списки чатов
        updateChatList(myPhone, otherUserPhone, text, timestamp)
        updateChatList(otherUserPhone, myPhone, text, timestamp)

        // МЫ УБРАЛИ ОТСЮДА ОТПРАВКУ ПУША, ЧТОБЫ НЕ БЫЛО ОШИБОК
    }

    private fun updateChatList(ownerPhone: String, chatWithPhone: String, lastMsg: String, time: Long) {
        val chatRef = FirebaseDatabase.getInstance().getReference("UserChats")
            .child(ownerPhone).child(chatWithPhone)
        val chatInfo = mapOf("phone" to chatWithPhone, "lastMessage" to lastMsg, "timestamp" to time)
        chatRef.updateChildren(chatInfo)
    }

    private fun notifyRecipientInternal() {
        FirebaseDatabase.getInstance().getReference("Users")
            .child(otherUserPhone).child("hasNewMessage").setValue(true)
    }

    private fun loadOtherUserName() {
        FirebaseDatabase.getInstance().getReference("Users").child(otherUserPhone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: otherUserPhone
                    supportActionBar?.title = name
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// --- ДАННЫЕ И АДАПТЕР ---
data class PrivateMessage(
    var id: String = "",
    val senderId: String = "",
    val text: String = "",
    val time: String = "",
    val timestamp: Long = 0,

    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false
)

class PrivateChatAdapter(
    private val messages: List<PrivateMessage>,
    private val myPhone: String
) : RecyclerView.Adapter<PrivateChatAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view.findViewById(R.id.message_container)
        val text: TextView = view.findViewById(R.id.msg_text)
        val time: TextView = view.findViewById(R.id.msg_time)
        val statusIcon: ImageView = view.findViewById(R.id.msg_status_icon)
        val author: TextView = view.findViewById(R.id.msg_author)
        val replyLayout: LinearLayout = view.findViewById(R.id.reply_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val msg = messages[position]

        holder.text.text = msg.text
        holder.time.text = msg.time
        holder.author.visibility = View.GONE
        holder.replyLayout.visibility = View.GONE

        val params = holder.container.layoutParams as LinearLayout.LayoutParams
        val isMine = (msg.senderId == myPhone)

        if (isMine) {
            params.gravity = Gravity.END
            holder.container.setBackgroundResource(R.drawable.bg_message_sent)
            holder.statusIcon.visibility = View.VISIBLE

            if (msg.isRead) {
                holder.statusIcon.setColorFilter(Color.parseColor("#2196F3"), PorterDuff.Mode.SRC_IN)
            } else {
                holder.statusIcon.setColorFilter(Color.parseColor("#BDBDBD"), PorterDuff.Mode.SRC_IN)
            }
        } else {
            params.gravity = Gravity.START
            holder.container.setBackgroundResource(R.drawable.bg_message_received)
            holder.statusIcon.visibility = View.GONE
        }

        holder.container.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size
}