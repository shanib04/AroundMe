package com.colman.aroundme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.colman.aroundme.data.model.EventInteraction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventInteractionDao {
    @Query("SELECT * FROM event_interactions WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun getInteraction(eventId: String, userId: String): EventInteraction?

    @Query("SELECT * FROM event_interactions WHERE eventId = :eventId")
    suspend fun getInteractionsForEvent(eventId: String): List<EventInteraction>

    @Query("SELECT * FROM event_interactions WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    fun observeInteraction(eventId: String, userId: String): Flow<EventInteraction?>

    @Query("SELECT COUNT(*) FROM event_interactions WHERE userId = :userId AND voteType IS NOT NULL")
    suspend fun getValidationCountForUser(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(interaction: EventInteraction)
}
