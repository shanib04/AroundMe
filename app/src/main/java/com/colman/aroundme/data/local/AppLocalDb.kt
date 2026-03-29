package com.colman.aroundme.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.colman.aroundme.data.local.dao.EventDao
import com.colman.aroundme.data.local.dao.EventInteractionDao
import com.colman.aroundme.data.local.dao.UserDao
import com.colman.aroundme.data.model.Achievement
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventInteraction
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.model.User
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Database(
    entities = [User::class, Event::class, EventInteraction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppLocalDb : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun eventInteractionDao(): EventInteractionDao

    companion object {
        @Volatile
        private var INSTANCE: AppLocalDb? = null

        fun getInstance(context: Context): AppLocalDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLocalDb::class.java,
                    "aroundme_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object Converters {
    private val gson by lazy { Gson() }
    private val achievementListType by lazy {
        object : TypeToken<List<Achievement>>() {}.type
    }

    @TypeConverter
    @JvmStatic
    fun fromString(value: String?): List<String> {
        return value?.split("||")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    @TypeConverter
    @JvmStatic
    fun listToString(list: List<String>?): String {
        return list?.joinToString(separator = "||") ?: ""
    }

    @TypeConverter
    @JvmStatic
    fun fromAchievementHistory(value: String?): List<Achievement> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<Achievement>>(value, achievementListType) }.getOrDefault(emptyList())
    }

    @TypeConverter
    @JvmStatic
    fun achievementHistoryToString(list: List<Achievement>?): String {
        return if (list.isNullOrEmpty()) "" else gson.toJson(list, achievementListType)
    }

    @TypeConverter
    @JvmStatic
    fun fromVoteType(value: EventVoteType?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toVoteType(value: String?): EventVoteType? = value?.let(EventVoteType::valueOf)
}
