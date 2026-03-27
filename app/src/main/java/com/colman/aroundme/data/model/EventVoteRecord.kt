package com.colman.aroundme.data.model

/** Firestore "votes" subcollection doc. */
data class EventVoteRecord(
    val voteType: String = "", // "active" | "inactive"
    val updatedAt: Long = 0L
)

