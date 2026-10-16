package com.myride.app.ui.background

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myride.app.R
import com.myride.app.data.BackgroundComposer
import com.myride.app.data.BackgroundSource
import com.myride.app.data.CompositionLayout
import com.myride.app.data.LayerTransform
import kotlinx.coroutines.launch

enum class BackgroundPreset(val title: String, @DrawableRes val drawableRes: Int? = null) {
    Gradient("Градиент"),
    Studio("Студия"),
    Road("Дорога", R.drawable.bg_road),
    City("Город", R.drawable.bg_city),
    Nature("Природа", R.drawable.bg_nature),
    Spring("Весна", R.drawable.bg_spring),
    Summer("Лето", R.drawable.bg_summer),
    Autumn("Осень", R.drawable.bg_autumn),
    Winter("Зима", R.drawable.bg_winter),
    BlurredOriginal("Размытый оригинал"),
    Custom("Своё фото"),
}

data class BackgroundEditorState(
    val itemIndex: Int = -1,
    val preset: BackgroundPreset = BackgroundPreset.Gradient,
    val customBackground: Uri? = null,
    val vehicle: LayerTransform = LayerTransform(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class BackgroundEditorViewModel(private val composer: BackgroundComposer) : ViewModel() {
    var state by mutableStateOf(BackgroundEditorState())
        private set

    fun start(itemIndex: Int) {
        if (state.itemIndex != itemIndex) state = BackgroundEditorState(itemIndex = itemIndex)
    }

    fun select(preset: BackgroundPreset) {
        if (!state.isSaving) state = state.copy(preset = preset, message = null, error = null)
    }

    fun selectCustom(uri: Uri) {
        if (!state.isSaving) {
            state = state.copy(
                preset = BackgroundPreset.Custom,
                customBackground = uri,
                message = null,
                error = null,
            )
        }
    }

    fun transform(panX: Float, panY: Float, zoom: Float) {
        if (state.isSaving) return
        val current = state.vehicle
        state = state.copy(
            vehicle = current.copy(
                offsetX = (current.offsetX + panX).coerceIn(-0.75f, 0.75f),
                offsetY = (current.offsetY + panY).coerceIn(-0.75f, 0.75f),
                scale = (current.scale * zoom).coerceIn(0.35f..2.4f),
            ),
        )
    }

    fun zoomSelected(factor: Float) = transform(0f, 0f, factor)

    fun resetLayout() {
        if (!state.isSaving) {
            state = state.copy(vehicle = LayerTransform())
        }
    }

    fun save(cutout: Bitmap, original: Uri) {
        if (state.isSaving) return
        val background = runCatching { source(original) }.getOrElse {
            state = state.copy(error = it.message ?: "Не выбран фон")
            return
        }
        val layout = CompositionLayout(vehicle = state.vehicle)
        viewModelScope.launch {
            state = state.copy(isSaving = true, message = null, error = null)
            runCatching { composer.save(cutout, background, layout) }
                .onSuccess {
                    state = state.copy(isSaving = false, message = "Готово · Downloads/MyRide")
                }
                .onFailure {
                    state = state.copy(
                        isSaving = false,
                        error = it.message ?: "Не удалось сохранить изображение",
                    )
                }
        }
    }

    fun showError(message: String) {
        state = state.copy(error = message, message = null)
    }

    private fun source(original: Uri): BackgroundSource = when (state.preset) {
        BackgroundPreset.Gradient -> BackgroundSource.Gradient
        BackgroundPreset.Studio -> BackgroundSource.Studio
        BackgroundPreset.Road,
        BackgroundPreset.City,
        BackgroundPreset.Nature,
        BackgroundPreset.Spring,
        BackgroundPreset.Summer,
        BackgroundPreset.Autumn,
        BackgroundPreset.Winter,
        -> BackgroundSource.BuiltIn(checkNotNull(state.preset.drawableRes))
        BackgroundPreset.BlurredOriginal -> BackgroundSource.BlurredOriginal(original)
        BackgroundPreset.Custom -> BackgroundSource.Photo(
            state.customBackground ?: error("Сначала выберите фотографию для фона"),
        )
    }
}
