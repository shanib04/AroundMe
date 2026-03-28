package com.colman.aroundme.data.remote.places

import com.google.gson.annotations.SerializedName

data class PlacesResponse(
    @SerializedName("results") val results: List<Place> = emptyList(),
    @SerializedName("status") val status: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class Place(
    @SerializedName("place_id") val placeId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("vicinity") val vicinity: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("rating") val rating: Double? = null,
    @SerializedName("user_ratings_total") val userRatingsTotal: Int? = null,
    @SerializedName("geometry") val geometry: Geometry? = null
)

data class Geometry(
    @SerializedName("location") val location: Location? = null
)

data class Location(
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lng") val lng: Double? = null
)