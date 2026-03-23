package com.colman.aroundme.data.repository

import android.content.Context
import android.util.Log
import com.colman.aroundme.data.local.AppLocalDb
import com.colman.aroundme.data.local.dao.EventDao
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Repository pattern for Event data
class EventRepository private constructor(
    private val eventDao: EventDao,
    private val firebase: FirebaseModel
) {

    fun observeAll(): Flow<List<Event>> = eventDao.observeAll()

    // Compatibility method for existing UI code
    fun getEvents(): Flow<List<Event>> = observeAll()

    fun getById(id: String) = eventDao.getById(id)

    // Compatibility method name used by older viewmodels/fragments
    fun getEventById(id: String) = getById(id)

    // Observe events for a specific publisher (LiveData from Room)
    fun observeEventsByPublisher(pubId: String) = eventDao.getEventsByPublisher(pubId)

    suspend fun upsertEvent(event: Event, pushToRemote: Boolean = true) {
        eventDao.insert(event)
        if (pushToRemote) {
            firebase.pushEvent(event)
        }
    }

    suspend fun deleteEvent(id: String) {
        eventDao.deleteById(id)
        // optionally remove from firebase
    }

    suspend fun deleteEventsByPublisher(pubId: String, removeRemote: Boolean = true) {
        eventDao.deleteEventsByPublisher(pubId)
        if (removeRemote) {
            try {
                firebase.deleteUserAndEvents(pubId)
            } catch (_: Exception) {
                // ignore remote failures
            }
        }
    }

    fun syncFromRemote(since: Long = 0L) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = firebase.fetchEventsSince(since)
                for (r in remote) {
                    // insert/replace local record
                    eventDao.insert(r)
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncFromRemote failed", e)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: EventRepository? = null
        private const val TAG = "EventRepository"

        fun getInstance(context: Context): EventRepository = INSTANCE ?: synchronized(this) {
            val db = AppLocalDb.getInstance(context)
            val repo = EventRepository(db.eventDao(), FirebaseModel.getInstance())
            INSTANCE = repo
            repo
        }
    }
}
