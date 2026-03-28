package com.colman.aroundme.data.remote.places

import retrofit2.http.GET
import retrofit2.http.Query

// Google Places (Legacy) Nearby Search endpoint. Docs: https://developers.google.com/maps/documentation/places/web-service/search-nearby
interface PlacesApiService {

    @GET("maps/api/place/nearbysearch/json")
    suspend fun nearbySearch(
        @Query("location") location: String,
        @Query("radius") radiusMeters: Int,
        @Query("type") type: String,
        @Query("key") apiKey: String
    ): PlacesNearbyResponse
}
