package com.example.project250311.Map.network

import com.example.project250311.Map.model.DirectionsResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsService {
    @GET("directions/json")
    fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "walking",
        @Query("key") apiKey: String
    ): Call<DirectionsResponse>
}
