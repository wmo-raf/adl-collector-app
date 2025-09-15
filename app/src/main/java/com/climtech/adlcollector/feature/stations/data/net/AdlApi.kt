package com.climtech.adlcollector.feature.stations.data.net

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

interface AdlApi {
    @GET
    @Headers("Accept: application/json")
    suspend fun getStations(@Url fullUrl: String): Response<List<Station>>
}