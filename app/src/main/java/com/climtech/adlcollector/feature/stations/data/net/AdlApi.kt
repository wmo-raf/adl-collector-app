package com.climtech.adlcollector.feature.stations.data.net

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface AdlApi {
    @GET
    suspend fun getStations(@Url fullUrl: String): Response<List<Station>>
}