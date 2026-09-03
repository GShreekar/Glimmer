package com.glimmer.app.data

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

/**
 * SEC-02, existing-install half: turns an already-populated PLAINTEXT "glimmer_database" file
 * into a SQLCipher-encrypted one, in place, without losing data. A fresh install never runs this
 * — [AppDatabase] just creates the file encrypted from the start.
 *
 * Uses SQLCipher's own documented recipe for encrypting an existing plaintext SQLite file: open
 * it with an empty password (SQLCipher then reads it as ordinary plaintext SQLite), `ATTACH` a
 * new database with the real key, and use `sqlcipher_export` to copy every table/index across in
 * one transaction. Opening via the original file path lets SQLite pick up any pending `-wal` data
 * automatically, so a checkpoint that hasn't happened yet isn't silently dropped.
 */
object DatabaseEncryptionMigrator {
    private const val MARKER_SUFFIX = ".encrypted"

    /**
     * Returns true if [dbName] is now (or already was) encrypted and safe to open with
     * [passphrase]. Returns false if migration was skipped because it failed — in which case the
     * original plaintext file is left completely untouched and the caller should open it
     * unencrypted for this run rather than force an encrypted open against a file that isn't
     * actually encrypted; migration is retried on the next launch.
     */
    fun migrateIfNeeded(context: Context, dbName: String, passphrase: ByteArray): Boolean {
        val dbFile = context.getDatabasePath(dbName)
        val parent = dbFile.parentFile ?: return false
        val markerFile = File(parent, dbFile.name + MARKER_SUFFIX)

        if (!dbFile.exists()) {
            // Fresh install, or the DB was cleared: nothing to migrate. AppDatabase will create
            // the file directly with SQLCipher, so record that as already "encrypted".
            markerFile.createNewFile()
            return true
        }
        if (markerFile.exists()) return true // already migrated in a previous run

        val tempFile = File(parent, dbFile.name + ".migrating").apply { delete() }
        var plaintext: SQLiteDatabase? = null
        try {
            plaintext = SQLiteDatabase.openDatabase(dbFile.path, "", null, SQLiteDatabase.OPEN_READWRITE)
            val hexKey = passphrase.joinToString("") { "%02x".format(it) }
            plaintext.rawExecSQL("ATTACH DATABASE '${tempFile.path}' AS encrypted KEY \"x'$hexKey'\"")
            plaintext.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plaintext.rawExecSQL("DETACH DATABASE encrypted")
            plaintext.close()
            plaintext = null

            if (!tempFile.exists() || tempFile.length() == 0L) {
                error("sqlcipher_export did not produce a database file")
            }

            // Only now — with a verified, non-empty encrypted copy sitting in tempFile — is it
            // safe to remove the plaintext original. Its -wal/-shm/-journal sidecars are tied to
            // ITS page layout, not the new encrypted file about to take its place at the same path.
            listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"), File(dbFile.path + "-journal"))
                .forEach { it.delete() }

            if (!tempFile.renameTo(dbFile)) {
                error("failed to move the encrypted database into place")
            }
            markerFile.createNewFile()
            true
        } catch (t: Throwable) {
            GLog.e(
                "DbMigration",
                "Plaintext-to-encrypted database migration failed; keeping the existing " +
                    "plaintext database for this run rather than risk losing it",
                t
            )
            tempFile.delete()
            false
        } finally {
            plaintext?.close()
        }
    }
}
