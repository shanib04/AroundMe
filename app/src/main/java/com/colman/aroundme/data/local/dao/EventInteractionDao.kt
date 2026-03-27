package com.colman.aroundme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.colman.aroundme.data.model.EventInteraction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventInteractionDao {
    @Query("SELECT * FROM event_interactions WHERE eventId = :eventId AND actorId = :actorId LIMIT 1")
    suspend fun getInteraction(eventId: String, actorId: String): EventInteraction?

    @Query("SELECT * FROM event_interactions WHERE eventId = :eventId")
    suspend fun getInteractionsForEvent(eventId: String): List<EventInteraction>

    @Query("SELECT * FROM event_interactions WHERE eventId = :eventId AND actorId = :actorId LIMIT 1")
    fun observeInteraction(eventId: String, actorId: String): Flow<EventInteraction?>

    @Query("SELECT COUNT(*) FROM event_interactions WHERE actorId = :actorId AND voteType IS NOT NULL")
    suspend fun getValidationCountForActor(actorId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(interaction: EventInteraction)
}
