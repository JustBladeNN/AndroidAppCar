package com.myride.app.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

class MediaAccessRepository(private val resolver: ContentResolver) {
    fun persistedFolder(): Uri? = resolver.persistedUriPermissions
        .lastOrNull { it.isReadPermission }
        ?.uri

    fun retainFolder(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun retainPhotos(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun folderLabel(uri: Uri?): String? = uri?.let {
        runCatching { DocumentsContract.getTreeDocumentId(it).substringAfterLast(':') }
            .getOrNull()
            ?.ifBlank { "Выбранная папка" }
    }
}

