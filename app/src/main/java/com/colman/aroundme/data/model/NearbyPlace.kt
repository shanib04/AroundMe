package com.colman.aroundme.data.model

/** UI model for a nearby essential place from Google Places. */
data class NearbyPlace(
    val name: String,
    val vicinity: String,
    val iconUrl: String,
    val photoUrl: String? = null,
    val rating: Double?,
    val ratingsTotal: Int?,
    val placeId: String?
)
