package com.glimmer.app.data

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

/** One birthday found on-device, ready to become a [Birthday] once the user confirms the import. */
data class ContactBirthdayCandidate(
    val name: String,
    val dateOfBirth: Long,
    val birthYear: Int?,
    val contactLookupKey: String?,
    // The contact's saved photo, if any — see PickedContact.photoUri for why this can't ever be
    // a WhatsApp picture. Copied into app-private storage (PhotoStorage) only for the candidates
    // actually imported, not for every contact scanned.
    val photoUri: String?
)

/**
 * FEAT-02: finds every contact with a saved birthday. Requires READ_CONTACTS — unlike FEAT-01's
 * single-contact picker, a bulk scan genuinely needs to read the whole address book, so this is
 * only ever invoked from the explicitly opt-in Import screen after the user grants that
 * permission there (never requested elsewhere, never on first launch).
 */
object ContactsBirthdayImporter {
    suspend fun findBirthdays(context: Context): List<ContactBirthdayCandidate> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ContactBirthdayCandidate>()
        val projection = arrayOf(
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.PHOTO_URI
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
        )
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
                val dateIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                while (cursor.moveToNext()) {
                    val name = nameIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                    val rawDate = dateIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                    val lookupKey = lookupIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                    val photoUri = photoIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                    if (name.isNullOrBlank() || rawDate.isNullOrBlank()) continue
                    val (millis, year) = parseContactDate(rawDate) ?: continue
                    results.add(ContactBirthdayCandidate(name, millis, year, lookupKey, photoUri))
                }
            }
        } catch (t: Throwable) {
            GLog.e("ContactsImport", "Failed to query contacts for birthdays", t)
        }
        results
    }

    /**
     * `Event.START_DATE` is normally ISO "yyyy-MM-dd", but is "--MM-dd" (no year) for a birthday
     * saved without one — common from Facebook-era imports, which is exactly what FEAT-05 exists
     * to represent. A handful of OEM contact apps write non-ISO formats; those are simply skipped
     * (returns null) rather than guessed at.
     */
    private fun parseContactDate(raw: String): Pair<Long, Int?>? = try {
        if (raw.startsWith("--")) {
            val monthDay = MonthDay.parse(raw)
            // Any leap year works as the scratch value here — placeholderDateOfBirth re-derives
            // month/day from it and re-applies its OWN fixed placeholder year regardless.
            val scratchMillis = monthDay.atYear(2000).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            placeholderDateOfBirth(scratchMillis) to null
        } else {
            val date = LocalDate.parse(raw)
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to date.year
        }
    } catch (e: Exception) {
        null
    }
}
