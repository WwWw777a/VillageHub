package com.example.villagehub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Класс-Адаптер. Он берет список (busList) и создает для каждой строки вид (View)
class BusAdapter(private val busList: List<BusRun>) : RecyclerView.Adapter<BusAdapter.BusViewHolder>() {

    // Эта штука "держит" элементы дизайна, чтобы мы могли в них писать текст
    class BusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val time: TextView = itemView.findViewById(R.id.bus_time)
        val direction: TextView = itemView.findViewById(R.id.bus_direction)
    }

    // 1. Создает пустой шаблон билетика (из файла item_bus.xml)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bus, parent, false)
        return BusViewHolder(view)
    }

    // 2. Заполняет этот билетик данными (время и направление)
    override fun onBindViewHolder(holder: BusViewHolder, position: Int) {
        val bus = busList[position]
        holder.time.text = bus.time
        holder.direction.text = bus.direction
    }

    // 3. Говорит списку, сколько всего у нас рейсов
    override fun getItemCount(): Int {
        return busList.size
    }
}