package com.glimmer.app.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * SEC-02: the Room database used to be plain, unencrypted SQLite in app-private storage. That's
 * fine against other apps, but not against device forensics or a rooted device — and for an app
 * whose entire positioning is "private", the DB holding every contact's name, birth date,
 * relationship and private notes deserves at-rest encryption, not just app-sandbox isolation.
 *
 * This generates a random 256-bit passphrase once and stores it in EncryptedSharedPreferences —
 * itself backed by a hardware-derived Android Keystore key, so the passphrase is never sitting on
 * disk in the clear. [AppDatabase] uses this passphrase to open the database via SQLCipher.
 */
object DatabaseKeyProvider {
    private const val PREFS_FILE = "glimmer_db_key"
    private const val KEY_ENTRY = "db_passphrase"
    private const val KEY_SIZE_BYTES = 32 // 256-bit, SQLCipher's default/recommended key size

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        prefs.getString(KEY_ENTRY, null)?.let { stored ->
            return Base64.decode(stored, Base64.NO_WRAP)
        }

        val newKey = ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_ENTRY, Base64.encodeToString(newKey, Base64.NO_WRAP)).apply()
        return newKey
    }
}
