package com.glimmer.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * BUG: a picked photo would vanish after the app was killed and reopened. `photoUri` was storing
 * the raw URI handed back by `PickVisualMedia`/the contact picker — for the Photo Picker, Android
 * documents that read grant as lasting only "for the duration of your app's process lifetime," so
 * the very next cold start (any time Android kills the process to reclaim memory, not just an
 * explicit force-stop) left Coil trying to load a URI it no longer had permission to read.
 *
 * The fix is to copy the bytes into app-private storage immediately on pick, and store a `file://`
 * URI to that copy instead — a location the app owns forever, no permission grant involved.
 *
 * Deliberately does NOT auto-delete "the old photo" on persist: whether an old file is safe to
 * delete depends on whether anything still references it (a Birthday row in the DB) or it's just
 * an in-progress draft — only the caller knows which, so that decision is left to them (see
 * Add/EditBirthdayScreen and GlimmerViewModel.deleteBirthday).
 */
object PhotoStorage {
    private const val PHOTOS_DIR = "photos"

    /** Copies [sourceUri]'s bytes into app-private storage; returns a durable `file://` URI, or null if the copy failed. */
    fun persistPickedPhoto(context: Context, sourceUri: Uri): String? {
        val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val destFile = File(dir, "${UUID.randomUUID()}.jpg")
        return try {
            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied) {
                destFile.delete()
                return null
            }
            destFile.toURI().toString()
        } catch (t: Throwable) {
            GLog.e("PhotoStorage", "Failed to persist a picked photo", t)
            destFile.delete()
            null
        }
    }

    /** Deletes a photo file this object created — a no-op (and never touches anything else) for any other URI. */
    fun deleteManagedPhoto(context: Context, photoUri: String) {
        try {
            val path = Uri.parse(photoUri).path ?: return
            val file = File(path)
            val photosDir = File(context.filesDir, PHOTOS_DIR).canonicalFile
            if (file.canonicalFile.parentFile == photosDir) file.delete()
        } catch (t: Throwable) {
            GLog.w("PhotoStorage", "Failed to delete a managed photo file", t)
        }
    }
}
