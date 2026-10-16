package com.myride.app.ui.background

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage

@Composable
fun BackgroundEditorScreen(
    viewModel: BackgroundEditorViewModel,
    itemIndex: Int,
    original: Uri,
    cutout: Bitmap,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    val context = LocalContext.current
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::selectCustom)
    }
    val savePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.save(cutout, original)
        else viewModel.showError("Без разрешения Android 9 не может сохранить файл в Downloads")
    }
    val saveResult = remember(context, viewModel, cutout, original) {
        {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.save(cutout, original)
            } else {
                savePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(itemIndex) { viewModel.start(itemIndex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = onBack, enabled = !state.isSaving) { Text("Назад") }
            Text("РЕДАКТОР", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Настройте автомобиль",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "Перетаскивайте выбранный слой. Щипок двумя пальцами меняет размер.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            CompositionPreview(
                state = state,
                original = original,
                cutout = cutout,
                onTransform = viewModel::transform,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
            )

            Text("Размер автомобиля", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { viewModel.zoomSelected(0.88f) }, modifier = Modifier.weight(1f)) {
                    Text("−  Размер")
                }
                OutlinedButton(onClick = { viewModel.zoomSelected(1.14f) }, modifier = Modifier.weight(1f)) {
                    Text("+  Размер")
                }
                OutlinedButton(onClick = viewModel::resetLayout, modifier = Modifier.weight(1f)) {
                    Text("Сброс")
                }
            }

            Text("Фон", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BackgroundPreset.entries) { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = {
                            if (preset == BackgroundPreset.Custom) {
                                backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } else {
                                viewModel.select(preset)
                            }
                        },
                        enabled = !state.isSaving,
                        label = { Text(preset.title) },
                    )
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp)) }
        }

        Button(
            onClick = saveResult,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Сохраняем…", modifier = Modifier.padding(start = 10.dp))
            } else {
                Text("Сохранить изображение", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompositionPreview(
    state: BackgroundEditorState,
    original: Uri,
    cutout: Bitmap,
    onTransform: (Float, Float, Float) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF11151C))
            .pointerInput(state.isSaving) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!state.isSaving) {
                        onTransform(
                            pan.x / size.width.coerceAtLeast(1).toFloat(),
                            pan.y / size.height.coerceAtLeast(1).toFloat(),
                            zoom,
                        )
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        BackgroundLayer(state.preset, original, state.customBackground)
        Image(
            bitmap = cutout.asImageBitmap(),
            contentDescription = "Автомобиль на выбранном фоне",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.68f)
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .graphicsLayer {
                    scaleX = state.vehicle.scale
                    scaleY = state.vehicle.scale
                    translationX = state.vehicle.offsetX * canvasWidth
                    translationY = state.vehicle.offsetY * canvasHeight
                },
        )
    }
}

@Composable
private fun BackgroundLayer(preset: BackgroundPreset, original: Uri, customBackground: Uri?) {
    when (preset) {
        BackgroundPreset.Gradient -> Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF143D69), Color(0xFF121C2D), Color(0xFF080C14))),
            ),
        )
        BackgroundPreset.Studio -> StudioBackground()
        BackgroundPreset.Road,
        BackgroundPreset.City,
        BackgroundPreset.Nature,
        BackgroundPreset.Spring,
        BackgroundPreset.Summer,
        BackgroundPreset.Autumn,
        BackgroundPreset.Winter,
        -> Image(
            painter = painterResource(checkNotNull(preset.drawableRes)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        BackgroundPreset.BlurredOriginal -> AsyncImage(
            model = original,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.12f, scaleY = 1.12f).blur(18.dp),
        )
        BackgroundPreset.Custom -> AsyncImage(
            model = customBackground,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StudioBackground() {
    Canvas(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF6A707C), Color(0xFF30353E), Color(0xFF101319))),
        ),
    ) {
        drawLine(
            color = Color(0x554A5260),
            start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.7f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.7f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
