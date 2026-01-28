package com.example.villagehub

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val messages: List<Message>,
    private val myPhone: String,
    private val onMessageClick: (Message) -> Unit,
    private val onUserClick: (Message) -> Unit,
    // НОВОЕ: Обработчик клика именно по реакциям (для просмотра списка админом)
    private val onReactionClick: (Message) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view.findViewById(R.id.message_container)
        val author: TextView = view.findViewById(R.id.msg_author)
        val text: TextView = view.findViewById(R.id.msg_text)
        val time: TextView = view.findViewById(R.id.msg_time)

        // Элементы цитаты
        val replyLayout: LinearLayout = view.findViewById(R.id.reply_layout)
        val replyAuthor: TextView = view.findViewById(R.id.reply_author)
        val replyText: TextView = view.findViewById(R.id.reply_text)

        val statusIcon: ImageView? = view.findViewById(R.id.msg_status_icon)

        // Плашка непрочитанных сообщений
        val unreadHeader: TextView = view.findViewById(R.id.tv_unread_header)

        // Поле для отображения реакций (смайликов)
        val reactionsText: TextView = view.findViewById(R.id.tv_reactions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        // --- ЛОГИКА ОТОБРАЖЕНИЯ ПЛАШКИ НЕПРОЧИТАННЫХ ---
        if (message.isHeader) {
            holder.unreadHeader.visibility = View.VISIBLE
        } else {
            holder.unreadHeader.visibility = View.GONE
        }
        // ------------------------------------------------

        holder.text.text = message.text
        holder.time.text = message.time

        // --- ЛОГИКА ОТОБРАЖЕНИЯ РЕАКЦИЙ (ОБНОВЛЕНО) ---
        // Используем reactionsCounts (цифры), чтобы видели все пользователи
        if (message.reactionsCounts.isNotEmpty()) {
            holder.reactionsText.visibility = View.VISIBLE

            // Формируем строку вида "👍 2  ❤️ 1" из счетчиков
            val sb = StringBuilder()
            for ((emoji, count) in message.reactionsCounts) {
                if (count > 0) {
                    sb.append("$emoji $count  ")
                }
            }
            holder.reactionsText.text = sb.toString().trim()

            // Обработка клика по реакции
            holder.reactionsText.setOnClickListener {
                // Передаем клик в Activity, где проверим:
                // Если Админ -> покажем список.
                // Если Пользователь -> можно сделать toggle лайка или ничего.
                onReactionClick(message)
            }
        } else {
            holder.reactionsText.visibility = View.GONE
        }
        // ------------------------------------------

        // --- ЛОГИКА ОТОБРАЖЕНИЯ ЦИТАТЫ ---
        if (message.replyToText.isNotEmpty()) {
            holder.replyLayout.visibility = View.VISIBLE
            holder.replyAuthor.text = "${message.replyToAuthor}:"
            holder.replyText.text = message.replyToText
        } else {
            holder.replyLayout.visibility = View.GONE
        }

        val params = holder.container.layoutParams as LinearLayout.LayoutParams

        // Сравниваем телефоны, чтобы понять, чье сообщение
        if (message.senderId == myPhone) {
            // === МОЕ СООБЩЕНИЕ ===
            params.gravity = Gravity.END
            holder.container.setBackgroundResource(R.drawable.bg_message_sent)
            holder.author.visibility = View.GONE
            holder.statusIcon?.visibility = View.VISIBLE
        } else {
            // === ЧУЖОЕ СООБЩЕНИЕ ===
            params.gravity = Gravity.START
            holder.container.setBackgroundResource(R.drawable.bg_message_received)
            holder.author.visibility = View.VISIBLE
            holder.statusIcon?.visibility = View.GONE

            val roleToShow = if (message.role.isEmpty()) "Житель" else message.role
            holder.author.text = "${message.author} ($roleToShow)"

            when (roleToShow) {
                "АДМИН" -> holder.author.setTextColor(Color.parseColor("#2E7D32"))
                "МОДЕРАТОР" -> holder.author.setTextColor(Color.parseColor("#D32F2F"))
                else -> holder.author.setTextColor(Color.parseColor("#F57C00"))
            }

            holder.author.setOnClickListener {
                onUserClick(message)
            }
        }
        holder.container.layoutParams = params

        holder.itemView.setOnClickListener {
            onMessageClick(message)
        }

        holder.itemView.setOnLongClickListener {
            onMessageClick(message)
            true
        }
    }

    override fun getItemCount() = messages.size
}