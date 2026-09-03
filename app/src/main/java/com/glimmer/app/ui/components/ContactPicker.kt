package com.glimmer.app.ui.components

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** What FEAT-01's contact picker hands back — any field can be missing/null on a sparse contact. */
data class PickedContact(
    val name: String?,
    val phoneNumber: String?,
    val lookupKey: String?
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
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                onPicked(
                    PickedContact(
                        name = nameIdx.takeIf { it >= 0 }?.let { cursor.getString(it) },
                        phoneNumber = numberIdx.takeIf { it >= 0 }?.let { cursor.getString(it) },
                        lookupKey = lookupIdx.takeIf { it >= 0 }?.let { cursor.getString(it) }
                    )
                )
            }
        }
    }
    return {
        launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }
}
