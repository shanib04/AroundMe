package com.colman.aroundme.data.model

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "event_interactions",
    primaryKeys = ["eventId", "actorId"],
    indices = [Index(value = ["eventId"])]
)
data class EventInteraction(
    val eventId: String = "",
    val actorId: String = "",
    val voteType: EventVoteType? = null,
    val rating: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

