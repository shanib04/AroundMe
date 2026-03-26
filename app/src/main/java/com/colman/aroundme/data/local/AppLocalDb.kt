package com.colman.aroundme.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.colman.aroundme.data.local.dao.EventDao
import com.colman.aroundme.data.local.dao.EventInteractionDao
import com.colman.aroundme.data.local.dao.UserDao
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventInteraction
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.model.User
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverter

@Database(
    entities = [User::class, Event::class, EventInteraction::class],
    version = 6,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new users table with the new schema
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        profileImageUrl TEXT,
                        email TEXT,
                        points INTEGER NOT NULL,
                        eventsPublishedCount INTEGER NOT NULL,
                        validationsMadeCount INTEGER NOT NULL,
                        rankTitle TEXT,
                        lastUpdated INTEGER NOT NULL
                    )"""
                )

                // Copy data from the old users table, mapping possible old column names to new ones.
                // If the old schema had `fullName` / `imageUrl` those will be copied; otherwise fallback to existing columns.
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO users_new (id, name, profileImageUrl, email, points, eventsPublishedCount, validationsMadeCount, rankTitle, lastUpdated)
                    SELECT id,
                           COALESCE(fullName, name) as name,
                           COALESCE(imageUrl, profileImageUrl) as profileImageUrl,
                           COALESCE(email, '') as email,
                           COALESCE(points, 0) as points,
                           COALESCE(eventsPublishedCount, 0) as eventsPublishedCount,
                           COALESCE(validationsMadeCount, 0) as validationsMadeCount,
                           COALESCE(rankTitle, 'Newcomer') as rankTitle,
                           COALESCE(lastUpdated, 0) as lastUpdated
                    FROM users
                    """
                )

                database.execSQL("DROP TABLE IF EXISTS users")
                database.execSQL("ALTER TABLE users_new RENAME TO users")

                // Create new events table with the new schema (tags stored as TEXT via converters)
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS events_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        publisherId TEXT,
                        title TEXT,
                        description TEXT,
                        category TEXT,
                        tags TEXT,
                        imageUrl TEXT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        geohash TEXT,
                        locationName TEXT,
                        publishTime INTEGER NOT NULL,
                        expirationTime INTEGER NOT NULL,
                        timeRemaining TEXT,
                        activeVotes INTEGER NOT NULL,
                        inactiveVotes INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )"""
                )

                // Copy existing events data; if old columns differ, COALESCE will pick available ones.
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO events_new (id, publisherId, title, description, category, tags, imageUrl, latitude, longitude, geohash, locationName, publishTime, expirationTime, timeRemaining, activeVotes, inactiveVotes, lastUpdated)
                    SELECT id,
                           COALESCE(publisherId, '') as publisherId,
                           COALESCE(title, '') as title,
                           COALESCE(description, '') as description,
                           COALESCE(category, '') as category,
                           COALESCE(tags, '') as tags,
                           COALESCE(imageUrl, '') as imageUrl,
                           COALESCE(latitude, 0.0) as latitude,
                           COALESCE(longitude, 0.0) as longitude,
                           COALESCE(geohash, '') as geohash,
                           COALESCE(locationName, '') as locationName,
                           COALESCE(publishTime, 0) as publishTime,
                           COALESCE(expirationTime, 0) as expirationTime,
                           COALESCE(timeRemaining, '') as timeRemaining,
                           COALESCE(activeVotes, 0) as activeVotes,
                           COALESCE(inactiveVotes, 0) as inactiveVotes,
                           COALESCE(lastUpdated, 0) as lastUpdated
                    FROM events
                    """
                )

                database.execSQL("DROP TABLE IF EXISTS events")
                database.execSQL("ALTER TABLE events_new RENAME TO events")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns bio and totalPoints to users
                database.execSQL("ALTER TABLE users ADD COLUMN bio TEXT DEFAULT ''")
                // Add username and displayName columns
                database.execSQL("ALTER TABLE users ADD COLUMN username TEXT DEFAULT ''")
                database.execSQL("ALTER TABLE users ADD COLUMN displayName TEXT DEFAULT ''")
                database.execSQL("ALTER TABLE users ADD COLUMN totalPoints INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE events ADD COLUMN averageRating REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE events ADD COLUMN ratingCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS event_interactions (
                        eventId TEXT NOT NULL,
                        actorId TEXT NOT NULL,
                        voteType TEXT,
                        rating INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY(eventId, actorId)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_event_interactions_eventId ON event_interactions(eventId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        username TEXT NOT NULL DEFAULT '',
                        displayName TEXT NOT NULL DEFAULT '',
                        profileImageUrl TEXT NOT NULL DEFAULT '',
                        email TEXT NOT NULL DEFAULT '',
                        bio TEXT NOT NULL DEFAULT '',
                        points INTEGER NOT NULL DEFAULT 0,
                        totalPoints INTEGER NOT NULL DEFAULT 0,
                        eventsPublishedCount INTEGER NOT NULL DEFAULT 0,
                        validationsMadeCount INTEGER NOT NULL DEFAULT 0,
                        rankTitle TEXT NOT NULL DEFAULT 'Newcomer',
                        lastUpdated INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT OR REPLACE INTO users_new (
                        id, name, username, displayName, profileImageUrl, email, bio,
                        points, totalPoints, eventsPublishedCount, validationsMadeCount,
                        rankTitle, lastUpdated
                    )
                    SELECT
                        id,
                        COALESCE(name, ''),
                        COALESCE(username, ''),
                        COALESCE(displayName, ''),
                        COALESCE(profileImageUrl, ''),
                        COALESCE(email, ''),
                        COALESCE(bio, ''),
                        COALESCE(points, 0),
                        COALESCE(totalPoints, 0),
                        COALESCE(eventsPublishedCount, 0),
                        COALESCE(validationsMadeCount, 0),
                        COALESCE(rankTitle, 'Newcomer'),
                        COALESCE(lastUpdated, 0)
                    FROM users
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE users")
                database.execSQL("ALTER TABLE users_new RENAME TO users")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        username TEXT NOT NULL DEFAULT '',
                        displayName TEXT NOT NULL DEFAULT '',
                        profileImageUrl TEXT NOT NULL DEFAULT '',
                        email TEXT NOT NULL DEFAULT '',
                        bio TEXT NOT NULL DEFAULT '',
                        points INTEGER NOT NULL DEFAULT 0,
                        totalPoints INTEGER NOT NULL DEFAULT 0,
                        eventsPublishedCount INTEGER NOT NULL DEFAULT 0,
                        validationsMadeCount INTEGER NOT NULL DEFAULT 0,
                        rankTitle TEXT NOT NULL DEFAULT 'Newcomer',
                        lastUpdated INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT OR REPLACE INTO users_new (
                        id, username, displayName, profileImageUrl, email, bio,
                        points, totalPoints, eventsPublishedCount, validationsMadeCount,
                        rankTitle, lastUpdated
                    )
                    SELECT
                        id,
                        COALESCE(username, ''),
                        COALESCE(displayName, ''),
                        COALESCE(profileImageUrl, ''),
                        COALESCE(email, ''),
                        COALESCE(bio, ''),
                        COALESCE(points, 0),
                        COALESCE(totalPoints, 0),
                        COALESCE(eventsPublishedCount, 0),
                        COALESCE(validationsMadeCount, 0),
                        COALESCE(rankTitle, 'Newcomer'),
                        COALESCE(lastUpdated, 0)
                    FROM users
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE users")
                database.execSQL("ALTER TABLE users_new RENAME TO users")
            }
        }

        fun getInstance(context: Context): AppLocalDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLocalDb::class.java,
                    "aroundme_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL(
                                """
                                INSERT OR REPLACE INTO users (
                                    id, username, displayName, profileImageUrl, email, bio,
                                    points, totalPoints, eventsPublishedCount, validationsMadeCount,
                                    rankTitle, lastUpdated
                                ) VALUES (
                                    'demo_publisher', 'aroundme', 'AroundMe Team', '', '', '',
                                    0, 0, 0, 0, 'Newcomer', 0
                                )
                                """.trimIndent()
                            )
                        }
                    })
                    .build()
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

    @TypeConverter
    @JvmStatic
    fun fromVoteType(value: EventVoteType?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toVoteType(value: String?): EventVoteType? = value?.let(EventVoteType::valueOf)
}
