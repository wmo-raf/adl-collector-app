package com.climtech.adlcollector.app

import android.net.Uri

sealed class Route(val route: String) {
    data object Login : Route("login")

    data object Splash : Route("splash")

    data object Main : Route("main/{tenantId}") {
        fun build(tenantId: String) = "main/$tenantId"
    }

    //  Station detail
    data object StationDetail : Route("station_detail/{tenantId}/{stationId}/{stationName}") {
        fun build(tenantId: String, stationId: Long, stationName: String) =
            "station_detail/$tenantId/$stationId/${Uri.encode(stationName)}"
    }

    // Observation form outside the bottom bar
    data object ObservationForm : Route("observation_form/{tenantId}/{stationId}/{stationName}") {
        fun build(tenantId: String, stationId: Long, stationName: String) =
            "observation_form/$tenantId/$stationId/${Uri.encode(stationName)}"
    }

    data object ObservationDetail : Route("observation_detail/{tenantId}/{obsKey}") {
        fun build(tenantId: String, obsKey: String) =
            "observation_detail/$tenantId/${Uri.encode(obsKey)}"
    }
}