package com.example.villagehub

import java.io.Serializable
import java.util.ArrayList

data class Ad(
    var id: String = "",
    val title: String = "",
    val price: String = "",
    val description: String = "",
    val phone: String = "",
    val author: String = "",
    // Используем ArrayList, так как он поддерживает Serializable
    val imageUrls: ArrayList<String> = ArrayList(),
    val timestamp: Long = 0
) : Serializable