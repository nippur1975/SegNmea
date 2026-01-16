package com.example.tracking_app

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

data class Channel(
    val id: String,
    val name: String,
    @DrawableRes val icon: Int,
    @ColorRes val color: Int
)
