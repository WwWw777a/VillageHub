package com.example.villagehub

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ImageSliderAdapter(private val images: List<String>) : RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.slider_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        // Мы используем файл разметки item_slider_image.xml (создадим его следующим шагом)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slider_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageString = images[position]
        try {
            val imageBytes = Base64.decode(imageString, Base64.DEFAULT)
            Glide.with(holder.itemView.context)
                .load(imageBytes)
                .fitCenter() // Чтобы картинка влезала целиком и не обрезалась
                .into(holder.imageView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int = images.size
}