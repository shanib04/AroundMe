package com.colman.aroundme.data.repository

import com.colman.aroundme.data.remote.places.NetworkClient
import com.colman.aroundme.data.remote.places.Place
import com.colman.aroundme.data.remote.places.PlacesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlacesRepository private constructor(
    private val placesApi: PlacesApi
) {

    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        type: String
    ): Result<List<Place>> = withContext(Dispatchers.IO) {
        try {
            val request = placesApi.getNearbyPlaces(
                location = "${latitude},${longitude}",
                radius = radiusMeters,
                type = type,
                key = null
            )

            val response = request.execute()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body.results)
                } else {
                    Result.failure(IllegalStateException("Places response body is null"))
                }
            } else {
                Result.failure(
                    IllegalStateException(
                        "Places request failed with code ${response.code()}"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlacesRepository? = null

        fun getInstance(): PlacesRepository = INSTANCE ?: synchronized(this) {
            val repository = PlacesRepository(NetworkClient.placesApiClient)
            INSTANCE = repository
            repository
        }
    }
}
