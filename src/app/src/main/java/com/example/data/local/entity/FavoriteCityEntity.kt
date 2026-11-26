package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_cities")
data class FavoriteCityEntity(
    @PrimaryKey val name: String,
    val lat: Double,
    val lon: Double,
    val dateAdded: Long = System.currentTimeMillis()
)
