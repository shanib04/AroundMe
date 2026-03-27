package com.colman.aroundme.data.repository

import com.colman.aroundme.BuildConfig
import com.colman.aroundme.data.model.NearbyPlace
import com.colman.aroundme.data.remote.places.PlacesApiService
import com.colman.aroundme.data.remote.places.PlacesRetrofitClient

class PlacesRepository(
    private val api: PlacesApiService
) {

    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        type: String
    ): Result<List<NearbyPlace>> = runCatching {
        val apiKey = BuildConfig.MAPS_API_KEY
        require(apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE") {
            "Missing Google API key. Please set MAPS_API_KEY in local.properties"
        }

        val response = api.nearbySearch(
            location = "${latitude},${longitude}",
            radiusMeters = radiusMeters,
            type = type,
            apiKey = apiKey
        )

        if (!response.errorMessage.isNullOrBlank()) {
            error(response.errorMessage)
        }

        response.results.mapNotNull { r ->
            val name = r.name?.trim().orEmpty()
            val vicinity = r.vicinity?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            NearbyPlace(
                name = name,
                vicinity = vicinity,
                iconUrl = r.icon.orEmpty(),
                photoUrl = null,
                rating = r.rating,
                ratingsTotal = r.userRatingsTotal,
                placeId = r.placeId
            )
        }
    }

    companion object {
        @Volatile private var INSTANCE: PlacesRepository? = null

        fun getInstance(): PlacesRepository = INSTANCE ?: synchronized(this) {
            val service = PlacesRetrofitClient.createService(isDebug = BuildConfig.DEBUG)
            val repo = PlacesRepository(service)
            INSTANCE = repo
            repo
        }
    }
}
