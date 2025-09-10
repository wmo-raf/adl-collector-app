package com.climtech.adlcollector

data class TenantConfig(
    val id: String,
    val name: String,
    val authorizeEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val scopes: List<String> = listOf("adl.read", "adl.write")
)
