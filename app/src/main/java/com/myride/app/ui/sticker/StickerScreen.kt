package com.myride.app.ui.sticker

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.myride.app.R
import com.myride.app.ui.share

@Composable
fun StickerScreen(
    viewModel: StickerViewModel,
    initialPhotos: List<Uri>,
    onPhotosSelected: (List<Uri>) -> Unit,
    onEditBackground: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    val context = LocalContext.current
    var previewIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var activeIndex by rememberSaveable { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) {
            previewIndex = null
            activeIndex = 0
            viewModel.selectPhotos(uris)
            onPhotosSelected(uris)
        }
    }
    val savePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.save() else viewModel.showError("Без разрешения Android 9 не может сохранить файл в Downloads")
    }
    val saveResults = remember(context, viewModel) {
        {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.save()
            } else {
                savePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.useInitialPhotos(initialPhotos) }
    LaunchedEffect(state.sharePayload) {
        state.sharePayload?.let {
            context.share(it.uris, it.mimeType)
            viewModel.shareHandled()
        }
    }

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
            OutlinedButton(onClick = onBack) { Text("Назад") }
            Text("СТИКЕР-СТУДИЯ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Text("Что создаём?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            StickerMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = {
                        previewIndex = null
                        viewModel.selectMode(mode)
                    },
                    label = { Text(mode.title) },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Фотографии автомобиля", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.mode == StickerMode.Gif) "Выберите от 2 до 8 кадров" else "Можно подготовить до 8 стикеров сразу",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.selectedPhotos.isEmpty()) "Выбрать фотографии" else "Заменить фотографии (${state.selectedPhotos.size})")
                }
                MediaPreview(state.selectedPhotos)
            }
        }

        if (state.cutouts.isNotEmpty()) {
            Text("Результат · нажмите для просмотра", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
            CutoutPreview(state.cutouts, onClick = {
                activeIndex = it
                previewIndex = it
            })
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }

        Spacer(Modifier.weight(1f))
        if (state.isProcessing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(progressText(state), modifier = Modifier.padding(start = 10.dp))
            }
        } else if (state.cutouts.isEmpty()) {
            Button(
                onClick = viewModel::process,
                enabled = state.selectedPhotos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            ) {
                Text("Вырезать машину", fontWeight = FontWeight.Bold)
            }
        } else {
            ResultActions(
                onEdit = { onEditBackground(activeIndex.coerceIn(state.cutouts.indices)) },
                onSave = saveResults,
                onShare = viewModel::export,
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
        }
    }

    previewIndex?.let { index ->
        val original = state.selectedPhotos.getOrNull(index)
        val cutout = state.cutouts.getOrNull(index)
        if (original != null && cutout != null) {
            ResultPreviewDialog(
                original = original,
                cutout = cutout,
                position = index + 1,
                total = state.cutouts.size,
                isProcessing = state.isProcessing,
                onDismiss = { if (!state.isProcessing) previewIndex = null },
                onReprocess = { viewModel.reprocess(index) },
                onEditBackground = {
                    previewIndex = null
                    onEditBackground(index)
                },
            )
        }
    }
}

private fun progressText(state: StickerUiState): String = state.progressLabel
    ?: if (state.processedCount > 0) {
        "Обработано ${state.processedCount} из ${state.selectedPhotos.size}"
    } else {
        "Подготавливаем результат…"
    }

@Composable
private fun MediaPreview(uris: List<Uri>) {
    if (uris.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uris, key = Uri::toString) { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(78.dp).background(Color(0xFF161B23), RoundedCornerShape(14.dp)),
            )
        }
    }
}

@Composable
private fun CutoutPreview(bitmaps: List<Bitmap>, onClick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(bitmaps) { index, bitmap ->
            CutoutOnCheckerboard(
                bitmap = bitmap,
                modifier = Modifier
                    .size(92.dp)
                    .clickable { onClick(index) },
            )
        }
    }
}

private enum class PreviewMode(val title: String) {
    Before("До"),
    After("После"),
    Compare("Сравнить"),
}

@Composable
private fun ResultPreviewDialog(
    original: Uri,
    cutout: Bitmap,
    position: Int,
    total: Int,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onReprocess: () -> Unit,
    onEditBackground: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(PreviewMode.After) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Назад") }
                    Text("Результат $position из $total", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(64.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    PreviewMode.entries.forEach { item ->
                        FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.title) })
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when (mode) {
                        PreviewMode.Before -> OriginalPreview(original, Modifier.fillMaxSize())
                        PreviewMode.After -> CutoutOnCheckerboard(cutout, Modifier.fillMaxSize())
                        PreviewMode.Compare -> Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PreviewPane("До", Modifier.weight(1f)) { OriginalPreview(original, Modifier.fillMaxSize()) }
                            PreviewPane("После", Modifier.weight(1f)) { CutoutOnCheckerboard(cutout, Modifier.fillMaxSize()) }
                        }
                    }
                    if (isProcessing) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                Text(
                    "Повторная обработка оставит крупнейший найденный объект — это полезно для коллажей и фотографий с несколькими машинами.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                OutlinedButton(
                    onClick = onReprocess,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Повторить · оставить главный объект")
                }
                Button(
                    onClick = onEditBackground,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_action_edit), contentDescription = null)
                    Text("Редактировать", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultActions(
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        ResultAction(R.drawable.ic_action_edit, "Редактировать", onEdit, Modifier.weight(1f))
        ResultAction(R.drawable.ic_action_save, "Сохранить", onSave, Modifier.weight(1f))
        ResultAction(R.drawable.ic_action_share, "Поделиться", onShare, Modifier.weight(1f))
    }
}

@Composable
private fun ResultAction(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(painterResource(icon), contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PreviewPane(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(title, modifier = Modifier.padding(bottom = 6.dp), fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun OriginalPreview(uri: Uri, modifier: Modifier) {
    AsyncImage(
        model = uri,
        contentDescription = "Исходная фотография",
        contentScale = ContentScale.Fit,
        modifier = modifier.background(Color.Black),
    )
}

@Composable
private fun CutoutOnCheckerboard(bitmap: Bitmap, modifier: Modifier) {
    Box(modifier.background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Checkerboard(Modifier.fillMaxSize(), 16.dp)
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Вырезанный автомобиль",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp).aspectRatio(1f, matchHeightConstraintsFirst = true),
        )
    }
}

@Composable
private fun Checkerboard(modifier: Modifier, cellSize: Dp) {
    val light = Color(0xFFE4E7EB)
    val dark = Color(0xFFB8BEC6)
    Canvas(modifier) {
        val cell = cellSize.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var column = 0
            var x = 0f
            while (x < size.width) {
                drawRect(
                    color = if ((row + column) % 2 == 0) light else dark,
                    topLeft = Offset(x, y),
                    size = Size(minOf(cell, size.width - x), minOf(cell, size.height - y)),
                )
                column++
                x += cell
            }
            row++
            y += cell
        }
    }
}
