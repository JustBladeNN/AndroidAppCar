# MyRide

MyRide is an Android photo editor for car owners. It separates a vehicle from the background, prepares square PNG stickers or GIF animations, and lets the user build a new composition without sending photos to a separate backend.

![Car placed on a new background](docs/example-result.jpg)

## Features

- select up to eight photos through the system Photo Picker;
- isolate the main subject with Google ML Kit;
- refine the segmentation mask and remove small disconnected objects;
- preview the original and processed image side by side;
- export transparent 512 × 512 PNG stickers or a GIF animation;
- save files to `Downloads/MyRide` or share them through Android;
- replace the background with a preset, a blurred original or another photo;
- move and scale the vehicle with touch gestures;

All editing is performed on the device. The application only receives access to media selected through Android system dialogs.

## Stack

- Kotlin and Jetpack Compose;
- Material 3;
- ViewModel and Kotlin coroutines;
- Google ML Kit Subject Segmentation;
- Coil 3 for image loading;
- MediaStore and FileProvider for saving and sharing;
- GifEncoder for GIF export.

## Project structure

```text
app/src/main/java/com/myride/app
├── data          image processing, export and media access
└── ui
    ├── background
    ├── home
    ├── sticker
    └── theme
```

The UI does not contain image-processing code. Segmentation, composition and export are kept in separate classes, while screen state is managed by ViewModels.

## Run

Requirements:

- Android Studio with JDK 25;
- Android SDK 37;
- a device or emulator running Android 9 or newer;
- Google Play services for ML Kit segmentation.

Open the repository in Android Studio and run the `app` configuration, or build from PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The first segmentation may take longer while Google Play services prepares the model.

## Verification

The project is checked with:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

There are currently no isolated JVM tests because the main workflow depends on Android graphics and ML Kit. The build and Android lint tasks are used as the repository checks.
