package com.climtech.adlcollector.feature.observations.data.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface ObservationsApi {
    @POST
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun submitObservation(
        @Url fullUrl: String, @Body payload: ObservationPostRequest
    ): Response<ObservationPostResponse>
}