package com.glimmer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

// exportSchema = true (with room.schemaLocation set in app/build.gradle.kts) writes a versioned
// JSON schema to app/schemas/ on every build — commit those files. Without them, MigrationTestHelper
// has nothing to migrate FROM, so a migration like MIGRATION_2_3 below can't be tested against the
// real starting schema, only asserted about by reading the code.
@Database(entities = [Birthday::class, Reminder::class], version = 8, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun birthdayDao(): BirthdayDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DB_NAME = "glimmer_database"

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

        // PERF-03: adds the denormalized "next occurrence" sort/search key. strftime('%m%d', …)
        // reads dateOfBirth (stored as UTC-midnight epoch millis — see BirthdayDates.kt) as a
        // zero-padded "MMDD" string ("0305"); CAST to INTEGER drops the leading zero the same way
        // BirthdayRepository.normalizeForStorage's Kotlin computation does (305, not "0305").
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE birthdays ADD COLUMN monthDayKey INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE birthdays SET monthDayKey = " +
                        "CAST(strftime('%m%d', dateOfBirth / 1000, 'unixepoch') AS INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_birthdays_monthDayKey ON birthdays(monthDayKey)")
            }
        }

        // FEAT-01: lets a person be linked back to the Contacts row they were picked from.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE birthdays ADD COLUMN contactLookupKey TEXT")
            }
        }

        // FEAT-04: one row per (person, offset) reminder, replacing the single reminderTime
        // string. Existing rows are carried forward as a single reminder matching whatever
        // offset their reminderTime already mapped to — nobody's existing reminder silently
        // disappears; reminderTime itself is left in place on `birthdays` (see the @Deprecated
        // note on Birthday.reminderTime) rather than risk a table-recreate migration to drop it.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        birthdayId INTEGER NOT NULL,
                        daysBefore INTEGER NOT NULL,
                        FOREIGN KEY(birthdayId) REFERENCES birthdays(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_birthdayId ON reminders(birthdayId)")
                db.execSQL(
                    """
                    INSERT INTO reminders (birthdayId, daysBefore)
                    SELECT id, CASE reminderTime
                        WHEN 'On the day' THEN 0
                        WHEN '3 days before' THEN 3
                        WHEN '1 week before' THEN 7
                        ELSE 1
                    END
                    FROM birthdays WHERE reminderEnabled = 1
                    """.trimIndent()
                )
            }
        }

        // FEAT-05: null means "year not known" — existing rows all have a real one, backfilled
        // from dateOfBirth (which, pre-FEAT-05, always carried the true year).
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE birthdays ADD COLUMN birthYear INTEGER")
                db.execSQL(
                    "UPDATE birthdays SET birthYear = " +
                        "CAST(strftime('%Y', dateOfBirth / 1000, 'unixepoch') AS INTEGER)"
                )
            }
        }

        // FEAT-12: pin-to-top favorites, defaulting everyone's existing rows to not-favorited.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE birthdays ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        // SEC-02: the database used to be plain, unencrypted SQLite. Now it's opened through
        // SQLCipher with a random passphrase generated once and kept in Android
        // Keystore-backed EncryptedSharedPreferences (DatabaseKeyProvider) — never hardcoded,
        // never derived from anything guessable.
        private fun buildDatabase(context: Context): AppDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(context)
            // Existing installs have a plaintext DB on disk from before this change; migrate it
            // in place. A fresh install has nothing to migrate and this returns true immediately.
            val encrypted = DatabaseEncryptionMigrator.migrateIfNeeded(context, DB_NAME, passphrase)

            val builder = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                )
            if (encrypted) {
                builder.openHelperFactory(SupportFactory(passphrase))
            }
            // If `encrypted` is false, migration failed this run (already logged by the
            // migrator) — open whatever is actually on disk (still plaintext, still intact)
            // rather than force an encrypted open that would fail outright and strand the
            // user's data. Migration is retried on the next launch.
            return builder.build()
        }
    }
}
