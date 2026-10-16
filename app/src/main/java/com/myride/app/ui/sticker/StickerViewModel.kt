package com.myride.app.ui.sticker

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myride.app.data.StickerExporter
import com.myride.app.data.VehicleCutoutProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StickerMode(val title: String) {
    Sticker("Стикер"),
    Gif("GIF"),
}

data class SharePayload(val uris: List<Uri>, val mimeType: String)

data class StickerUiState(
    val mode: StickerMode = StickerMode.Sticker,
    val selectedPhotos: List<Uri> = emptyList(),
    val cutouts: List<Bitmap> = emptyList(),
    val isProcessing: Boolean = false,
    val processedCount: Int = 0,
    val progressLabel: String? = null,
    val error: String? = null,
    val message: String? = null,
    val sharePayload: SharePayload? = null,
)

class StickerViewModel(
    private val processor: VehicleCutoutProcessor,
    private val exporter: StickerExporter,
) : ViewModel() {
    var state by mutableStateOf(StickerUiState())
        private set

    fun useInitialPhotos(uris: List<Uri>) {
        if (state.selectedPhotos.isEmpty() && uris.isNotEmpty()) selectPhotos(uris)
    }

    fun selectMode(mode: StickerMode) {
        if (state.isProcessing) return
        state = state.copy(mode = mode, cutouts = emptyList(), error = null, message = null, sharePayload = null)
    }

    fun selectPhotos(uris: List<Uri>) {
        if (state.isProcessing) return
        state = state.copy(
            selectedPhotos = uris.take(MAX_PHOTOS),
            cutouts = emptyList(),
            error = null,
            message = null,
            sharePayload = null,
        )
    }

    fun process() {
        val photos = state.selectedPhotos
        val mode = state.mode
        if (photos.isEmpty() || state.isProcessing) return
        if (mode == StickerMode.Gif && photos.size < 2) {
            state = state.copy(error = "Для GIF выберите минимум две фотографии")
            return
        }

        viewModelScope.launch {
            state = state.copy(
                isProcessing = true,
                cutouts = emptyList(),
                processedCount = 0,
                progressLabel = null,
                error = null,
                message = null,
            )
            runCatching {
                val maxSide = maxSide(mode)
                buildList {
                    photos.forEachIndexed { index, uri ->
                        add(processor.cutOut(uri, maxSide))
                        state = state.copy(processedCount = index + 1)
                    }
                }
            }.onSuccess { cutouts ->
                state = state.copy(isProcessing = false, cutouts = cutouts, progressLabel = null)
            }.onFailure(::showFailure)
        }
    }

    fun reprocess(index: Int) {
        val uri = state.selectedPhotos.getOrNull(index) ?: return
        if (state.cutouts.getOrNull(index) == null || state.isProcessing) return
        viewModelScope.launch {
            state = state.copy(
                isProcessing = true,
                processedCount = 0,
                progressLabel = "Ищем главный объект…",
                error = null,
                message = null,
            )
            runCatching {
                processor.cutOut(uri, minOf(maxSide(state.mode), RETRY_MAX_SIDE), primaryOnly = true)
            }
                .onSuccess { cutout ->
                    val updated = state.cutouts.toMutableList().apply { this[index] = cutout }
                    state = state.copy(
                        isProcessing = false,
                        cutouts = updated,
                        progressLabel = null,
                        message = "Результат обновлён: оставлен главный объект",
                    )
                }
                .onFailure(::showFailure)
        }
    }

    fun export() {
        val cutouts = state.cutouts
        val mode = state.mode
        if (cutouts.isEmpty() || state.isProcessing) return
        viewModelScope.launch {
            state = state.copy(isProcessing = true, progressLabel = "Подготавливаем файлы…", error = null, message = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    when (mode) {
                        StickerMode.Sticker -> SharePayload(exporter.stickers(cutouts), "image/png")
                        StickerMode.Gif -> SharePayload(exporter.gif(cutouts), "image/gif")
                    }
                }
            }.onSuccess { payload ->
                state = state.copy(isProcessing = false, progressLabel = null, sharePayload = payload)
            }.onFailure(::showFailure)
        }
    }

    fun save() {
        val cutouts = state.cutouts
        val mode = state.mode
        if (cutouts.isEmpty() || state.isProcessing) return
        viewModelScope.launch {
            state = state.copy(isProcessing = true, progressLabel = "Сохраняем в Downloads…", error = null, message = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    when (mode) {
                        StickerMode.Sticker -> exporter.saveStickers(cutouts).size
                        StickerMode.Gif -> {
                            exporter.saveGif(cutouts)
                            1
                        }
                    }
                }
            }.onSuccess { count ->
                state = state.copy(
                    isProcessing = false,
                    progressLabel = null,
                    message = "Сохранено файлов: $count · Downloads/MyRide",
                )
            }.onFailure(::showFailure)
        }
    }

    fun showError(message: String) {
        state = state.copy(error = message, message = null)
    }

    fun shareHandled() {
        state = state.copy(sharePayload = null)
    }

    private fun showFailure(error: Throwable) {
        state = state.copy(
            isProcessing = false,
            progressLabel = null,
            error = error.message ?: "Не удалось выполнить операцию",
        )
    }

    private fun maxSide(mode: StickerMode) = if (mode == StickerMode.Sticker) 1_600 else 800

    override fun onCleared() = processor.close()

    private companion object {
        const val RETRY_MAX_SIDE = 1_200
        const val MAX_PHOTOS = 8
    }
}
