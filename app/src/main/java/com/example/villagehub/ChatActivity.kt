package com.example.villagehub

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class ChatActivity : AppCompatActivity() {

    private lateinit var databaseRef: DatabaseReference
    private lateinit var bannedRef: DatabaseReference
    private lateinit var pinnedRef: DatabaseReference
    private lateinit var userRef: DatabaseReference
    // НОВАЯ ССЫЛКА: Для хранения деталей реакций (кто именно лайкнул)
    private lateinit var reactionDetailsRef: DatabaseReference

    private lateinit var adapter: ChatAdapter
    private val messageList = mutableListOf<Message>()
    private val pinnedList = mutableListOf<PinnedItem>()

    private lateinit var myPhone: String
    private lateinit var myUserName: String
    private lateinit var myUserRole: String
    private var iAmAdmin: Boolean = false
    private var iAmMod: Boolean = false

    private var editingMessageId: String? = null
    private var replyingToMessage: Message? = null

    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var recyclerView: RecyclerView

    private lateinit var layoutReplyPreview: ConstraintLayout
    private lateinit var tvPreviewName: TextView
    private lateinit var tvPreviewText: TextView
    private lateinit var btnCloseReply: ImageView

    private lateinit var layoutPinned: CardView
    private lateinit var tvPinnedText: TextView
    private lateinit var btnUnpin: ImageView

    private lateinit var btnScrollDown: CardView

    private var lastReadTimestamp: Long = 0L
    private var firstUnreadPosition: Int = -1
    private var isFirstLoad = true

    // БОЛЬШОЙ СПИСОК ЭМОДЗИ
    private val emojiList = listOf(
        "👍", "👎", "❤️", "🔥", "😂", "😢", "😮", "😡", "🎉", "👏",
        "💩", "🤝", "🙏", "👀", "🤡", "👻", "👽", "🤖", "🎃", "☠️",
        "💯", "✅", "❌", "❓", "❗", "💤", "👋", "👌", "✌️", "🤞",
        "🤟", "🤘", "🤙", "🖕", "🧠", "🦷", "🦴", "👀", "👄", "💋",
        "💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟", "❣️", "💔",
        "👑", "💍", "💎", "⚽", "🏀", "🚗", "🏠", "💵", "💣", "🧨"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val sharedPref = getSharedPreferences("VillagePrefs", Context.MODE_PRIVATE)
        myUserName = sharedPref.getString("USER_NAME", "Пользователь") ?: "Пользователь"
        myUserRole = sharedPref.getString("USER_ROLE", "Житель") ?: "Житель"
        myPhone = sharedPref.getString("USER_PHONE", "") ?: ""

        iAmAdmin = (myUserRole == "АДМИН" || myUserRole == "admin")
        iAmMod = (myUserRole == "МОДЕРАТОР")

        val toolbar: Toolbar = findViewById(R.id.toolbar_chat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Чат поселка"

        recyclerView = findViewById(R.id.recycler_view_chat)
        inputField = findViewById(R.id.chat_input)
        sendButton = findViewById(R.id.btn_send_chat)
        btnScrollDown = findViewById(R.id.btn_scroll_down)

        layoutReplyPreview = findViewById(R.id.layout_reply_preview)
        tvPreviewName = findViewById(R.id.tv_preview_name)
        tvPreviewText = findViewById(R.id.tv_preview_text)
        btnCloseReply = findViewById(R.id.btn_close_reply)

        layoutPinned = findViewById(R.id.layout_pinned_msg)
        tvPinnedText = findViewById(R.id.tv_pinned_text)
        btnUnpin = findViewById(R.id.btn_unpin)
        btnUnpin.visibility = View.GONE

        // ИНИЦИАЛИЗАЦИЯ АДАПТЕРА
        adapter = ChatAdapter(
            messages = messageList,
            myPhone = myPhone,
            onMessageClick = { selectedMessage -> showActionDialog(selectedMessage, inputField) },
            onUserClick = { selectedMessage -> showActionDialog(selectedMessage, inputField) },
            // НОВЫЙ ОБРАБОТЧИК: Клик по смайликам
            onReactionClick = { message ->
                if (iAmAdmin) {
                    // Если Админ -> грузим список людей
                    loadAndShowWhoReacted(message.id)
                } else {
                    // Если Житель -> показываем просто тост или ничего
                    Toast.makeText(this, "Всего реакций: ${message.reactionsCounts.values.sum()}", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        btnScrollDown.setOnClickListener {
            if (messageList.isNotEmpty()) {
                recyclerView.scrollToPosition(messageList.size - 1)
                btnScrollDown.visibility = View.GONE
                markChatAsRead()
            }
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (recyclerView.canScrollVertically(1)) {
                    btnScrollDown.visibility = View.VISIBLE
                } else {
                    btnScrollDown.visibility = View.GONE
                    markChatAsRead()
                }
            }
        })

        databaseRef = FirebaseDatabase.getInstance().getReference("Chats")
        bannedRef = FirebaseDatabase.getInstance().getReference("BannedUsers")
        pinnedRef = FirebaseDatabase.getInstance().getReference("PinnedMessages")
        userRef = FirebaseDatabase.getInstance().getReference("Users").child(myPhone)
        // Инициализация ветки деталей реакций
        reactionDetailsRef = FirebaseDatabase.getInstance().getReference("ReactionDetails")

        checkIfImBanned()
        fetchLastReadTimeAndStartListening()
        listenForPinnedMessages()

        layoutPinned.setOnClickListener { showAllPinnedMessagesDialog() }

        sendButton.setOnClickListener {
            val text = inputField.text.toString()
            if (text.isNotEmpty()) {
                if (editingMessageId != null) {
                    updateMessage(editingMessageId!!, text)
                } else {
                    sendMessage(text)
                }
                inputField.text.clear()
                editingMessageId = null
                cancelReplyMode()
            }
        }
        btnCloseReply.setOnClickListener { cancelReplyMode() }
    }

    override fun onPause() {
        super.onPause()
        val lm = recyclerView.layoutManager as LinearLayoutManager
        val lastPos = lm.findLastCompletelyVisibleItemPosition()
        if (lastPos != -1 && lastPos >= messageList.size - 2) {
            markChatAsRead()
        }
    }

    private fun markChatAsRead() {
        if (myPhone.isEmpty() || messageList.isEmpty()) return
        val lastMessageTime = messageList.last().timestamp
        if (lastMessageTime > lastReadTimestamp) {
            lastReadTimestamp = lastMessageTime
            userRef.child("chatLastRead").setValue(lastReadTimestamp)
        }
    }

    private fun fetchLastReadTimeAndStartListening() {
        if (myPhone.isEmpty()) {
            listenForMessages()
            return
        }
        userRef.child("chatLastRead").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lastReadTimestamp = snapshot.getValue(Long::class.java) ?: 0L
                listenForMessages()
            }
            override fun onCancelled(error: DatabaseError) {
                listenForMessages()
            }
        })
    }

    private fun listenForMessages() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                firstUnreadPosition = -1
                var foundUnread = false

                for (postSnapshot in snapshot.children) {
                    val message = postSnapshot.getValue(Message::class.java)
                    if (message != null) {
                        message.id = postSnapshot.key ?: ""

                        // --- ОБНОВЛЕННАЯ ЛОГИКА ПАРСИНГА РЕАКЦИЙ ---
                        // Теперь мы ищем reactionsCounts (цифры)
                        val countsSnapshot = postSnapshot.child("reactionsCounts")
                        val tempCounts = HashMap<String, Int>()
                        for (c in countsSnapshot.children) {
                            val emoji = c.key
                            val count = c.getValue(Int::class.java) ?: 0
                            if (emoji != null && count > 0) {
                                tempCounts[emoji] = count
                            }
                        }
                        message.reactionsCounts = tempCounts

                        // Пытаемся понять, ставил ли Я лайк (для подсветки)
                        // В идеале это нужно брать из локальной базы или ReactionDetails,
                        // но для простоты пока оставим пустым или проверим ReactionDetails отдельно
                        // message.myReaction = ... (пока пропускаем, чтобы не перегружать сеть)
                        // ------------------------------------------

                        message.isMine = (message.senderId == myPhone)
                        message.isHeader = false

                        if (!message.isMine && message.timestamp > lastReadTimestamp && !foundUnread) {
                            message.isHeader = true
                            firstUnreadPosition = messageList.size
                            foundUnread = true
                        }
                        messageList.add(message)
                    }
                }
                adapter.notifyDataSetChanged()

                // Скролл (остается старый)
                recyclerView.post {
                    if (isFirstLoad) {
                        if (firstUnreadPosition != -1) {
                            (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(firstUnreadPosition, 100)
                            btnScrollDown.visibility = View.VISIBLE
                        } else {
                            if (messageList.isNotEmpty()) {
                                recyclerView.scrollToPosition(messageList.size - 1)
                                btnScrollDown.visibility = View.GONE
                                markChatAsRead()
                            }
                        }
                        isFirstLoad = false
                    } else {
                        if (editingMessageId == null && messageList.isNotEmpty()) {
                            val lm = recyclerView.layoutManager as LinearLayoutManager
                            val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                            if (messageList.size - lastVisible < 5) {
                                recyclerView.scrollToPosition(messageList.size - 1)
                                btnScrollDown.visibility = View.GONE
                                markChatAsRead()
                            } else {
                                btnScrollDown.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenForPinnedMessages() {
        pinnedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                pinnedList.clear()
                for (child in snapshot.children) {
                    val item = child.getValue(PinnedItem::class.java)
                    if (item != null) pinnedList.add(item)
                }
                pinnedList.sortBy { it.pinnedTime }

                if (pinnedList.isNotEmpty()) {
                    layoutPinned.visibility = View.VISIBLE
                    val lastPin = pinnedList.last()
                    if (pinnedList.size > 1) {
                        tvPinnedText.text = "${lastPin.text} (и еще ${pinnedList.size - 1})"
                    } else {
                        tvPinnedText.text = lastPin.text
                    }
                } else {
                    layoutPinned.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAllPinnedMessagesDialog() {
        if (pinnedList.isEmpty()) return
        val sortedForDisplay = pinnedList.reversed()
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

        val titles = sortedForDisplay.map {
            val timeStr = if (it.pinnedTime > 0) sdf.format(Date(it.pinnedTime)) else ""
            "🔹 ${it.author}  ($timeStr)\n${it.text}\n──────────────────────"
        }.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("📌 Закрепленные сообщения")
        builder.setItems(titles) { _, which ->
            val selectedPin = sortedForDisplay[which]
            if (iAmAdmin) {
                AlertDialog.Builder(this)
                    .setTitle("Управление")
                    .setMessage("Открепить это сообщение?\n\n\"${selectedPin.text}\"")
                    .setPositiveButton("Открепить") { _, _ -> unpinMessage(selectedPin.originalId) }
                    .setNegativeButton("Отмена", null).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(selectedPin.author)
                    .setMessage(selectedPin.text)
                    .setPositiveButton("ОК", null).show()
            }
        }
        builder.setPositiveButton("Закрыть", null)
        builder.show()
    }

    private fun pinMessage(message: Message) {
        val alreadyPinned = pinnedList.any { it.originalId == message.id }
        if (alreadyPinned) {
            Toast.makeText(this, "Уже закреплено!", Toast.LENGTH_SHORT).show()
            return
        }
        val pinnedItem = PinnedItem(
            text = message.text,
            author = message.author,
            originalId = message.id,
            pinnedTime = System.currentTimeMillis()
        )
        pinnedRef.child(message.id).setValue(pinnedItem)
        Toast.makeText(this, "Сообщение закреплено 📌", Toast.LENGTH_SHORT).show()
    }

    private fun unpinMessage(originalId: String) {
        pinnedRef.child(originalId).removeValue()
        Toast.makeText(this, "Сообщение откреплено", Toast.LENGTH_SHORT).show()
    }

    private fun openPrivateChat(otherPhone: String) {
        if (otherPhone.isEmpty()) return
        val chatId = if (myPhone < otherPhone) "${myPhone}_${otherPhone}" else "${otherPhone}_${myPhone}"
        val intent = Intent(this, PrivateChatActivity::class.java)
        intent.putExtra("CHAT_ID", chatId)
        intent.putExtra("OTHER_USER_PHONE", otherPhone)
        startActivity(intent)
    }

    private fun checkIfImBanned() {
        if (myPhone.isEmpty()) return
        bannedRef.child(myPhone).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val banEndTime = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    val banType = snapshot.child("type").getValue(String::class.java) ?: "GLOBAL"
                    val currentTime = System.currentTimeMillis()

                    if (banEndTime == -1L || banEndTime > currentTime) {
                        if (banType == "CHAT_FULL") {
                            Toast.makeText(this@ChatActivity, "Вам запрещен вход в чат!", Toast.LENGTH_LONG).show()
                            finish()
                        } else if (banType == "CHAT_WRITE" || banType == "GLOBAL") {
                            activateBanMode(banEndTime)
                        }
                    } else {
                        bannedRef.child(myPhone).removeValue()
                        disableBanMode()
                    }
                } else {
                    disableBanMode()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun activateBanMode(endTime: Long) {
        inputField.isEnabled = false
        sendButton.isEnabled = false
        sendButton.alpha = 0.5f
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        if (endTime == -1L) inputField.setText("ВЫ ЗАБЛОКИРОВАНЫ НАВСЕГДА")
        else inputField.setText("Бан (чтение) до ${sdf.format(Date(endTime))}")
        inputField.setTextColor(Color.RED)
    }

    private fun disableBanMode() {
        inputField.isEnabled = true
        sendButton.isEnabled = true
        sendButton.alpha = 1.0f
        val currentText = inputField.text.toString()
        if (currentText.contains("Бан") || currentText.contains("НАВСЕГДА")) inputField.text.clear()
        inputField.setTextColor(Color.BLACK)
    }

    private fun sendMessage(text: String) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        val currentTimeStr = sdf.format(Date())
        val timestamp = System.currentTimeMillis()

        val replyText = replyingToMessage?.text ?: ""
        val replyAuthor = replyingToMessage?.author ?: ""

        val newMessage = Message(
            author = myUserName,
            role = myUserRole,
            text = text,
            time = currentTimeStr,
            senderId = myPhone,
            timestamp = timestamp,
            replyToText = replyText,
            replyToAuthor = replyAuthor
        )
        databaseRef.push().setValue(newMessage)
        markChatAsRead()
    }

    private fun updateMessage(messageId: String, newText: String) {
        databaseRef.child(messageId).child("text").setValue(newText)
        Toast.makeText(this, "Сообщение изменено", Toast.LENGTH_SHORT).show()
    }

    private fun activateReplyMode(message: Message) {
        replyingToMessage = message
        layoutReplyPreview.visibility = View.VISIBLE
        tvPreviewName.text = "Ответ для: ${message.author}"
        tvPreviewText.text = message.text
        inputField.requestFocus()
    }

    private fun cancelReplyMode() {
        replyingToMessage = null
        layoutReplyPreview.visibility = View.GONE
    }

    private fun showActionDialog(message: Message, inputField: EditText) {
        val isTargetAdmin = message.role == "АДМИН" || message.role == "admin"
        val optionsList = mutableListOf<String>()

        optionsList.add("😍 Поставить реакцию")
        optionsList.add("Ответить")

        if (!message.isMine) optionsList.add("Написать лично")
        if (iAmAdmin) optionsList.add("📌 Закрепить сообщение")
        if (message.isMine) optionsList.add("Редактировать")
        if (message.isMine || iAmAdmin || (iAmMod && !isTargetAdmin)) optionsList.add("Удалить сообщение")
        if ((iAmAdmin || (iAmMod && !isTargetAdmin)) && !message.isMine) optionsList.add("ЗАБЛОКИРОВАТЬ ⛔")

        val options = optionsList.toTypedArray()
        AlertDialog.Builder(this).setTitle("Действия с ${message.author}").setItems(options) { _, which ->
            when (options[which]) {
                "😍 Поставить реакцию" -> showReactionGridDialog(message)
                "Ответить" -> activateReplyMode(message)
                "Написать лично" -> openPrivateChat(message.senderId)
                "📌 Закрепить сообщение" -> pinMessage(message)
                "Редактировать" -> {
                    editingMessageId = message.id
                    inputField.setText(message.text)
                    inputField.requestFocus()
                }
                "Удалить сообщение" -> if (message.id.isNotEmpty()) databaseRef.child(message.id).removeValue()
                "ЗАБЛОКИРОВАТЬ ⛔" -> showBanTypeDialog(message)
            }
        }.show()
    }

    private fun showReactionGridDialog(message: Message) {
        val context = this
        val scrollView = ScrollView(context)
        val gridLayout = GridLayout(context)
        gridLayout.columnCount = 5
        gridLayout.rowCount = GridLayout.UNDEFINED
        gridLayout.setPadding(16, 16, 16, 16)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Выберите реакцию")
            .setView(scrollView)
            .setNegativeButton("Отмена", null)
            .create()

        for (emoji in emojiList) {
            val emojiView = TextView(context)
            emojiView.text = emoji
            emojiView.textSize = 28f
            emojiView.gravity = Gravity.CENTER
            emojiView.setPadding(16, 16, 16, 16)

            // Если нужно подсвечивать свой выбор, нужно делать доп запрос в ReactionDetails,
            // но пока оставим без подсветки ради скорости.
            emojiView.setBackgroundColor(Color.TRANSPARENT)

            emojiView.setOnClickListener {
                toggleReaction(message, emoji)
                dialog.dismiss()
            }

            val params = GridLayout.LayoutParams()
            params.width = GridLayout.LayoutParams.WRAP_CONTENT
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.setMargins(8, 8, 8, 8)
            gridLayout.addView(emojiView, params)
        }

        scrollView.addView(gridLayout)
        dialog.show()
    }

    // === ОБНОВЛЕННАЯ ФУНКЦИЯ: РАЗДЕЛЬНАЯ ЗАПИСЬ (ПРИВАТНОСТЬ) ===
    private fun toggleReaction(message: Message, emoji: String) {
        // 1. Проверяем, ставил ли я уже этот лайк (нужен запрос к ReactionDetails)
        val myReactionRef = reactionDetailsRef.child(message.id).child(emoji).child(myPhone)

        myReactionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // УЖЕ СТОИТ -> УДАЛЯЕМ
                    // А. Удаляем имя из деталей (для админа)
                    myReactionRef.removeValue()

                    // Б. Уменьшаем счетчик в общем чате (для всех)
                    val countRef = databaseRef.child(message.id).child("reactionsCounts").child(emoji)
                    countRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val value = currentData.getValue(Int::class.java) ?: 0
                            if (value > 0) currentData.value = value - 1
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {}
                    })
                    Toast.makeText(this@ChatActivity, "Реакция удалена", Toast.LENGTH_SHORT).show()

                } else {
                    // НЕ СТОИТ -> СТАВИМ
                    // А. Записываем имя (только админ увидит, если настроены правила)
                    myReactionRef.setValue(myUserName)

                    // Б. Увеличиваем счетчик (видят все)
                    val countRef = databaseRef.child(message.id).child("reactionsCounts").child(emoji)
                    countRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val value = currentData.getValue(Int::class.java) ?: 0
                            currentData.value = value + 1
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {}
                    })
                    Toast.makeText(this@ChatActivity, "Поставлено: $emoji", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // === НОВАЯ ФУНКЦИЯ ДЛЯ АДМИНА: ПОКАЗАТЬ КТО ЛАЙКНУЛ ===
    private fun loadAndShowWhoReacted(messageId: String) {
        val ref = reactionDetailsRef.child(messageId)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sb = StringBuilder()
                if (!snapshot.exists()) {
                    Toast.makeText(this@ChatActivity, "Нет реакций", Toast.LENGTH_SHORT).show()
                    return
                }

                // Проходим по смайликам (ключи)
                for (emojiSnap in snapshot.children) {
                    val emoji = emojiSnap.key
                    sb.append("$emoji:\n")
                    // Проходим по людям внутри смайлика
                    for (userSnap in emojiSnap.children) {
                        val userName = userSnap.getValue(String::class.java)
                        val userPhone = userSnap.key // Это ID (телефон)
                        sb.append(" - $userName ($userPhone)\n")
                    }
                    sb.append("\n")
                }

                // Показываем диалог
                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("Кто отреагировал")
                    .setMessage(sb.toString())
                    .setPositiveButton("ОК", null)
                    .show()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ChatActivity, "Ошибка доступа", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showBanTypeDialog(message: Message) {
        val types = arrayOf("Только чтение (Мут)", "Полный бан чата (Кик)", "Глобальный бан")
        AlertDialog.Builder(this).setTitle("Наказание").setItems(types) { _, which ->
            val type = when(which) { 0->"CHAT_WRITE"; 1->"CHAT_FULL"; else->"GLOBAL" }
            showBanDurationDialog(message, type)
        }.show()
    }

    private fun showBanDurationDialog(message: Message, banType: String) {
        val banOptions = arrayOf("На 1 час", "На 3 часа", "На 6 часов", "На 12 часов", "На 24 часа", "Навсегда")
        AlertDialog.Builder(this).setTitle("Срок?").setItems(banOptions) { _, which ->
            val currentTime = System.currentTimeMillis()
            val hour = 3600000L
            val endTime = when(which) {
                0 -> currentTime + hour
                1 -> currentTime + (3*hour)
                2 -> currentTime + (6*hour)
                3 -> currentTime + (12*hour)
                4 -> currentTime + (24*hour)
                else -> -1L
            }
            val banData = mapOf("timestamp" to endTime, "type" to banType)
            bannedRef.child(message.senderId).setValue(banData)
            Toast.makeText(this, "Бан выдан!", Toast.LENGTH_SHORT).show()
        }.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

data class PinnedItem(
    val text: String = "",
    val author: String = "",
    val originalId: String = "",
    val pinnedTime: Long = 0
)