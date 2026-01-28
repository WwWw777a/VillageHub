package com.example.villagehub

import com.google.firebase.database.Exclude
import java.util.HashMap

// Важно: У всех полей должны быть значения по умолчанию (= ""),
// иначе Firebase не сможет прочитать данные.

data class Message(
    var id: String = "",
    val author: String = "",
    val role: String = "",        // <-- Убедись, что это поле есть!
    val text: String = "",
    val time: String = "",
    val senderId: String = "",

    // НОВОЕ ПОЛЕ: Точное время (нужно для логики непрочитанных)
    val timestamp: Long = 0,

    // СТАРОЕ ПОЛЕ: Хранит реакции (UserID -> Смайлик).
    // В новой логике: Админ получает этот список полным,
    // а для обычных пользователей сервер (или правила безопасности) должен отдавать его пустым.
    val reactions: HashMap<String, String> = HashMap(),

    // --- ДОБАВЛЕНО ДЛЯ ПРИВАТНОСТИ ---

    // 1. СЧЕТЧИКИ: Хранит количество реакций (Смайлик -> Количество).
    // Пример: "👍" -> 5, "🔥" -> 2.
    // Это поле видят ВСЕ пользователи.
    var reactionsCounts: HashMap<String, Int> = HashMap(),

    // 2. МОЯ РЕАКЦИЯ: Локальное поле.
    // Нужно, чтобы подсветить кнопку синим, если Я поставил лайк.
    // @Exclude значит, что оно не сохраняется в общую базу, а вычисляется в приложении.
    @Exclude var myReaction: String? = null,

    // ----------------------------------

    // Локальное поле (кто отправил)
    @Exclude var isMine: Boolean = false,

    // Поля для ответов
    val replyToText: String = "",
    val replyToAuthor: String = "",

    // НОВОЕ ПОЛЕ: Нужно ли показывать плашку "Непрочитанные" над этим сообщением
    @Exclude var isHeader: Boolean = false
)