package com.climtech.adlcollector.net

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Station(
    val id: Long,
    val name: String
)