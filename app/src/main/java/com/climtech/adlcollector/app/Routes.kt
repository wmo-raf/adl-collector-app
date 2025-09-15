package com.climtech.adlcollector.app

sealed class Route(val route: String) {
    data object Login : Route("login")

    data object Splash : Route("splash")

    data object Main : Route("main/{tenantId}") {
        fun build(tenantId: String) = "main/$tenantId"
    }
}