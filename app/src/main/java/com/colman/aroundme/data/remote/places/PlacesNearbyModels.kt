package com.colman.aroundme.data.remote.places

import com.google.gson.annotations.SerializedName

data class PlacesNearbyResponse(
    @SerializedName("results") val results: List<PlaceResult> = emptyList(),
    @SerializedName("status") val status: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class PlaceResult(
    @SerializedName("name") val name: String? = null,
    @SerializedName("vicinity") val vicinity: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("rating") val rating: Double? = null,
    @SerializedName("user_ratings_total") val userRatingsTotal: Int? = null,
    @SerializedName("place_id") val placeId: String? = null
)