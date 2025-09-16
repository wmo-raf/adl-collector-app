package com.climtech.adlcollector.app

import android.net.Uri

sealed class Route(val route: String) {
    data object Login : Route("login")

    data object Splash : Route("splash")

    data object Main : Route("main/{tenantId}") {
        fun build(tenantId: String) = "main/$tenantId"
    }
}

// --------- Nested nav route builders for Main screen ---------

object StationRoutes {
    fun detail(stationId: Long, stationName: String): String =
        "station/$stationId/${Uri.encode(stationName)}"
}

object ObservationRoutes {
    fun new(stationId: Long, stationName: String): String =
        "observation/new/$stationId/${Uri.encode(stationName)}"
}
