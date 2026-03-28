package com.colman.aroundme.data.remote.places

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface PlacesApi {

    @GET("maps/api/place/nearbysearch/json")
    fun getNearbyPlaces(
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("type") type: String,
        @Query("key") key: String? = null
    ): Call<PlacesResponse>
}