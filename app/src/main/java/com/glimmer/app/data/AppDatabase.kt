package com.glimmer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// exportSchema = true (with room.schemaLocation set in app/build.gradle.kts) writes a versioned
// JSON schema to app/schemas/ on every build — commit those files. Without them, MigrationTestHelper
// has nothing to migrate FROM, so a migration like MIGRATION_2_3 below can't be tested against the
// real starting schema, only asserted about by reading the code.
@Database(entities = [Birthday::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun birthdayDao(): BirthdayDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE birthdays ADD COLUMN notes TEXT")
            }
        }

        // Adds phoneNumber so the Detail screen's Message/Call actions can target the actual
        // person instead of opening an empty composer/dialer (see BirthdayDetailScreen).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE birthdays ADD COLUMN phoneNumber TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glimmer_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
