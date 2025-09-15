package com.climtech.adlcollector.app

sealed class BottomDest(val route: String, val label: String) {
    data object Stations : BottomDest("home", "Stations")
    data object Observations : BottomDest("observations", "Observations")
    data object Account : BottomDest("account", "Account")

    companion object {
        val items = listOf(Stations, Observations, Account)
    }
}