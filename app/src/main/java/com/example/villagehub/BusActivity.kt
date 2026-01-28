package com.example.villagehub

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView

class BusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus)

        // 1. Настройка шапки
        val toolbar: Toolbar = findViewById(R.id.toolbar_bus)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Главное меню" // <-- ТЕПЕРЬ ТАК

        // 2. Данные (БЕЗ ЦЕН)
        val schedule = listOf(
            BusRun("06:30", "В Город"),
            BusRun("07:15", "В Город"),
            BusRun("08:00", "В Город (Школьный)"),
            BusRun("09:30", "В Поселок (Обратно)"),
            BusRun("12:00", "В Город"),
            BusRun("13:15", "На Станцию"),
            BusRun("14:30", "В Поселок (Обратно)"),
            BusRun("17:40", "В Город (Рабочий)"),
            BusRun("19:00", "В Поселок (Последний)")
        )

        // 3. Загружаем данные в список
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view_bus)
        recyclerView.adapter = BusAdapter(schedule)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}