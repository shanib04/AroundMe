package com.colman.aroundme.data.model

/** Firestore "ratings" subcollection doc. */
data class EventRatingRecord(
    val score: Double = 0.0,
    val updatedAt: Long = 0L
)

