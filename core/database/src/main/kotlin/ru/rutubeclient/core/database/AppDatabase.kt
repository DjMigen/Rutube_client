package ru.rutubeclient.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HistoryEntity::class,
        SubscriptionEntity::class,
        BookmarkEntity::class,
        SearchQueryEntity::class,
        DownloadEntity::class,
        RatingEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun searchQueryDao(): SearchQueryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun ratingDao(): RatingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rutube_client.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ratings (" +
                "videoId TEXT PRIMARY KEY NOT NULL, " +
                "liked INTEGER NOT NULL, " +
                "ratedAt INTEGER NOT NULL)"
        )
    }
}
