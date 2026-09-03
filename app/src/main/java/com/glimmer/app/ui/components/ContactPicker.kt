package com.glimmer.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** What FEAT-01's contact picker hands back — any field can be missing/null on a sparse contact. */
data class PickedContact(
    val name: String?,
    val phoneNumber: String?,
    val lookupKey: String?,
    // The contact's photo, if they have one saved — a content:// URI good for the app's own
    // process lifetime only (same caveat as the system Photo Picker), so callers must copy it via
    // PhotoStorage.persistPickedPhoto before storing it on a Birthday. This is the ONLY photo
    // source actually reachable here: WhatsApp does not write its own profile picture back into
    // the system Contacts provider (nor expose one via any public API), so a contact's "DP" as
    // seen inside WhatsApp itself is not something any third-party app — this one included — can
    // read. What IS reachable is whatever photo is already saved on the system Contacts entry
    // (set via the Contacts app, or synced from a Google/other account).
    val photoUri: String?
)

/**
 * FEAT-01: launches the system contact picker scoped to phone numbers — `ACTION_PICK` on
 * `Phone.CONTENT_URI` opens the Contacts app's own UI and hands back exactly the one row the user
 * chose. This needs **no permission at all**: the picker runs out-of-process in Contacts, and the
 * URI Android returns only grants read access to that single row, not the address book. Reserve
 * READ_CONTACTS for FEAT-02's bulk import — a different, explicitly opt-in flow.
 *
 * Returns a plain callback (not a launcher object) so a call site can just do
 * `val pickContact = rememberContactPickerLauncher { picked -> ... }` and wire it straight to an
 * `onClick`.
 */
@Composable
fun rememberContactPickerLauncher(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                // PHOTO_URI on the phone-number row is only populated on some OEMs/API levels;
                // CONTACT_ID + a follow-up query against Contacts.CONTENT_URI is the reliable path.
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val contactIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                var photoUri = photoIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                if (photoUri.isNullOrBlank() && contactIdIdx >= 0) {
                    photoUri = queryContactPhotoUri(context, cursor.getLong(contactIdIdx))
                }
                onPicked(
                    PickedContact(
                        name = nameIdx.takeIf { it >= 0 }?.let { cursor.getString(it) },
                        phoneNumber = numberIdx.takeIf { it >= 0 }?.let { cursor.getString(it) },
                        lookupKey = lookupIdx.takeIf { it >= 0 }?.let { cursor.getString(it) },
                        photoUri = photoUri
                    )
                )
            }
        }
    }
    return {
        launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }
}

private fun queryContactPhotoUri(context: Context, contactId: Long): String? {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    return context.contentResolver.query(
        uri, arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}
