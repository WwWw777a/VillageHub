package com.example.villagehub

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class AdsAdapter(
    private val adsList: List<Ad>,
    private val onAdClick: (Ad) -> Unit
) : RecyclerView.Adapter<AdsAdapter.AdViewHolder>() {

    class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.ad_title)
        val price: TextView = itemView.findViewById(R.id.ad_price)
        val image: ImageView = itemView.findViewById(R.id.ad_image)
        val dateAuthor: TextView = itemView.findViewById(R.id.ad_date_author)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdViewHolder {
        // Используем item_ad.xml (дизайн с плитками)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ad, parent, false)
        return AdViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdViewHolder, position: Int) {
        val ad = adsList[position]

        // Заполняем текст
        holder.title.text = ad.title
        holder.price.text = "${ad.price} ₽"

        // ВЕРНУЛ ВРЕМЯ: формат день.месяц часы:минуты
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(ad.timestamp))
        holder.dateAuthor.text = "$dateStr • ${ad.author}"

        // Обработка картинки (Base64)
        if (ad.imageUrls.isNotEmpty()) {
            val imageString = ad.imageUrls[0] // Берем первую картинку для обложки
            try {
                val imageBytes = Base64.decode(imageString, Base64.DEFAULT)

                // Для нормального фото используем CENTER_CROP (заполнить квадрат)
                holder.image.scaleType = ImageView.ScaleType.CENTER_CROP

                Glide.with(holder.itemView.context)
                    .load(imageBytes)
                    .centerCrop()
                    .into(holder.image)
            } catch (e: Exception) {
                // Если ошибка — ставим заглушку по центру
                holder.image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                holder.image.setImageResource(android.R.drawable.ic_menu_camera)
            }
        } else {
            // Если фото нет — ставим заглушку по центру
            holder.image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            holder.image.setImageResource(android.R.drawable.ic_menu_camera)
        }

        // Обработка клика по всему объявлению
        holder.itemView.setOnClickListener {
            onAdClick(ad)
        }
    }

    override fun getItemCount(): Int {
        return adsList.size
    }
}