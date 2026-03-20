package com.colman.aroundme.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.colman.aroundme.data.local.dao.EventDao
import com.colman.aroundme.data.local.dao.UserDao
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import androidx.room.TypeConverter

@Database(
    entities = [User::class, Event::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppLocalDb : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppLocalDb? = null

        fun getInstance(context: Context): AppLocalDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLocalDb::class.java,
                    "aroundme_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object Converters {
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
}

