package com.colman.aroundme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.colman.aroundme.data.model.Event
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.LiveData

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY lastUpdated DESC")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun getById(id: String): Flow<Event?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: Event)

    @Update
    suspend fun update(event: Event)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM events WHERE lastUpdated > :since")
    suspend fun getEventsSince(since: Long): List<Event>

    // LiveData for events published by a specific user (useful for profile statistics)
    @Query("SELECT * FROM events WHERE publisherId = :pubId ORDER BY lastUpdated DESC")
    fun getEventsByPublisher(pubId: String): LiveData<List<Event>>

    @Query("SELECT COUNT(*) FROM events WHERE publisherId = :pubId")
    suspend fun getCountByPublisher(pubId: String): Int

    @Query("DELETE FROM events WHERE publisherId = :pubId")
    suspend fun deleteEventsByPublisher(pubId: String)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getByIdNow(id: String): Event?
}
