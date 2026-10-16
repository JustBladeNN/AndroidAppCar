package com.myride.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myride.app.data.BackgroundComposer
import com.myride.app.data.MediaAccessRepository
import com.myride.app.data.StickerExporter
import com.myride.app.data.VehicleCutoutProcessor
import com.myride.app.ui.background.BackgroundEditorScreen
import com.myride.app.ui.background.BackgroundEditorViewModel
import com.myride.app.ui.home.HomeScreen
import com.myride.app.ui.sticker.StickerScreen
import com.myride.app.ui.sticker.StickerViewModel

private enum class AppScreen { Home, StickerStudio, BackgroundEditor }

@Composable
fun MyRideApp() {
    val context = LocalContext.current.applicationContext
    val mediaRepository = remember { MediaAccessRepository(context.contentResolver) }
    val mainViewModel = viewModel { MainViewModel(mediaRepository) }
    val stickerViewModel = viewModel {
        StickerViewModel(
            processor = VehicleCutoutProcessor(context.contentResolver),
            exporter = StickerExporter(context),
        )
    }
    val backgroundViewModel = viewModel { BackgroundEditorViewModel(BackgroundComposer(context)) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.Home) }
    var backgroundItem by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = screen != AppScreen.Home) {
        screen = when (screen) {
            AppScreen.BackgroundEditor -> AppScreen.StickerStudio
            AppScreen.StickerStudio -> AppScreen.Home
            AppScreen.Home -> AppScreen.Home
        }
    }

    when (screen) {
        AppScreen.Home -> HomeScreen(
            state = mainViewModel.state,
            folderLabel = mainViewModel.folderLabel(),
            onFolderSelected = mainViewModel::selectFolder,
            onPhotosSelected = mainViewModel::selectPhotos,
            onOpenStickerStudio = { screen = AppScreen.StickerStudio },
        )

        AppScreen.StickerStudio -> StickerScreen(
            viewModel = stickerViewModel,
            initialPhotos = mainViewModel.state.selectedPhotos,
            onPhotosSelected = mainViewModel::selectPhotos,
            onEditBackground = { index ->
                backgroundItem = index
                screen = AppScreen.BackgroundEditor
            },
            onBack = { screen = AppScreen.Home },
        )

        AppScreen.BackgroundEditor -> {
            val original = stickerViewModel.state.selectedPhotos.getOrNull(backgroundItem)
            val cutout = stickerViewModel.state.cutouts.getOrNull(backgroundItem)
            if (original == null || cutout == null) {
                LaunchedEffect(Unit) { screen = AppScreen.StickerStudio }
            } else {
                BackgroundEditorScreen(
                    viewModel = backgroundViewModel,
                    itemIndex = backgroundItem,
                    original = original,
                    cutout = cutout,
                    onBack = { screen = AppScreen.StickerStudio },
                )
            }
        }
    }
}

fun Context.share(uris: List<android.net.Uri>, mimeType: String) {
    val action = if (uris.size == 1) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE
    val intent = android.content.Intent(action).apply {
        type = mimeType
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size == 1) {
            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
        } else {
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    startActivity(android.content.Intent.createChooser(intent, "Поделиться результатом").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}
