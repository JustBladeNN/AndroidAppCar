package com.myride.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myride.app.ui.MediaAccessState

@Composable
fun HomeScreen(
    state: MediaAccessState,
    folderLabel: String?,
    onFolderSelected: (Uri) -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
    onOpenStickerStudio: () -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onFolderSelected)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) onPhotosSelected(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("MYRIDE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Твоя машина. Твоя история.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Приложение видит только те папки и фотографии, которые вы выбрали сами.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Доступ к медиатеке", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                AccessRow("Папка", folderLabel ?: "Не выбрана")
                AccessRow("Фотографии", if (state.selectedPhotos.isEmpty()) "Не выбраны" else "Выбрано: ${state.selectedPhotos.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                        Text("Выбрать папку")
                    }
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Выбрать фото")
                    }
                }
                Text(
                    "Системное окно выбора закрывается жестом «Назад» или кнопкой Back эмулятора.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF12375B), Color(0xFF182333))),
                    RoundedCornerShape(24.dp),
                )
                .padding(22.dp),
        ) {
            Text("Стикер-студия", color = Color(0xFF9DCDFF), fontWeight = FontWeight.SemiBold)
            Text("Сделать стикеры для ТГ", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text("Выберем фотографии, вырежем машину и подготовим PNG-стикер или GIF.", color = Color(0xFFC7D6E6), modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = onOpenStickerStudio,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF55A7FF), contentColor = Color(0xFF06101B)),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) {
                Text("Открыть студию", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccessRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
