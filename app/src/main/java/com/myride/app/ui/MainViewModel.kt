package com.myride.app.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.myride.app.data.MediaAccessRepository

data class MediaAccessState(
    val folderUri: Uri? = null,
    val selectedPhotos: List<Uri> = emptyList(),
)

class MainViewModel(private val repository: MediaAccessRepository) : ViewModel() {
    var state by mutableStateOf(MediaAccessState(folderUri = repository.persistedFolder()))
        private set

    fun selectFolder(uri: Uri) {
        repository.retainFolder(uri)
        state = state.copy(folderUri = uri)
    }

    fun selectPhotos(uris: List<Uri>) {
        repository.retainPhotos(uris)
        state = state.copy(selectedPhotos = uris.distinct())
    }

    fun folderLabel(): String? = repository.folderLabel(state.folderUri)
}

