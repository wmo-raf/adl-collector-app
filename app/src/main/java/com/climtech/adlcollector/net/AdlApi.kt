package com.climtech.adlcollector.net

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface AdlApi {
    @GET
    suspend fun getStations(
        @Url fullUrl: String,
        @Header("Authorization") bearer: String
    ): Response<List<Station>>
}