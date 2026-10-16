package com.myride.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface BackgroundSource {
    data object Gradient : BackgroundSource
    data object Studio : BackgroundSource
    data class BuiltIn(@DrawableRes val resourceId: Int) : BackgroundSource
    data class Photo(val uri: Uri) : BackgroundSource
    data class BlurredOriginal(val uri: Uri) : BackgroundSource
}

data class LayerTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
)

data class CompositionLayout(
    val vehicle: LayerTransform = LayerTransform(),
)

class BackgroundComposer(private val context: Context) {
    private val downloads = DownloadStore(context)

    suspend fun save(
        cutout: Bitmap,
        background: BackgroundSource,
        layout: CompositionLayout,
    ): Uri = withContext(Dispatchers.IO) {
        val result = compose(cutout, background, layout)
        try {
            downloads.save("myride_background_${System.currentTimeMillis()}.jpg", "image/jpeg") { output ->
                check(result.compress(Bitmap.CompressFormat.JPEG, 94, output)) { "Не удалось записать изображение" }
            }
        } finally {
            result.recycle()
        }
    }

    private fun compose(cutout: Bitmap, background: BackgroundSource, layout: CompositionLayout): Bitmap {
        val result = createBitmap(OUTPUT_SIZE, OUTPUT_SIZE)
        val canvas = Canvas(result)
        drawBackground(canvas, background)
        drawVehicle(canvas, cutout, layout.vehicle)
        return result
    }

    private fun drawBackground(canvas: Canvas, background: BackgroundSource) {
        when (background) {
            BackgroundSource.Gradient -> drawGradient(canvas)
            BackgroundSource.Studio -> drawStudio(canvas)
            is BackgroundSource.BuiltIn -> drawBitmapBackground(
                canvas,
                BitmapFactory.decodeResource(context.resources, background.resourceId)
                    ?: error("Не удалось открыть встроенный фон"),
            )
            is BackgroundSource.Photo -> drawBitmapBackground(canvas, decode(background.uri))
            is BackgroundSource.BlurredOriginal -> drawBlurredBackground(canvas, decode(background.uri))
        }
    }

    private fun drawGradient(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                OUTPUT_SIZE.toFloat(),
                OUTPUT_SIZE.toFloat(),
                intArrayOf(Color.rgb(20, 60, 105), Color.rgb(18, 28, 45), Color.rgb(8, 12, 20)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat(), paint)
    }

    private fun drawStudio(canvas: Canvas) {
        val size = OUTPUT_SIZE.toFloat()
        canvas.drawColor(Color.rgb(22, 25, 31))
        val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size / 2,
                size * 0.34f,
                size * 0.72f,
                intArrayOf(Color.rgb(105, 112, 124), Color.rgb(48, 53, 62), Color.rgb(18, 21, 27)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size, size * 0.72f, wall)
        val floor = Paint().apply {
            shader = LinearGradient(0f, size * 0.7f, 0f, size, Color.rgb(43, 47, 54), Color.rgb(9, 11, 15), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, size * 0.7f, size, size, floor)
    }

    private fun drawBitmapBackground(canvas: Canvas, bitmap: Bitmap) {
        try {
            drawCenterCrop(canvas, bitmap, OUTPUT_SIZE)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawBlurredBackground(canvas: Canvas, bitmap: Bitmap) {
        try {
            val tiny = createBitmap(BLUR_SIZE, BLUR_SIZE)
            try {
                drawCenterCrop(Canvas(tiny), bitmap, BLUR_SIZE)
                canvas.drawBitmap(
                    tiny,
                    null,
                    Rect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            } finally {
                tiny.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, targetSize: Int) {
        val scale = maxOf(targetSize.toFloat() / bitmap.width, targetSize.toFloat() / bitmap.height)
        val width = (bitmap.width * scale).roundToInt()
        val height = (bitmap.height * scale).roundToInt()
        val left = (targetSize - width) / 2
        val top = (targetSize - height) / 2
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun drawVehicle(canvas: Canvas, cutout: Bitmap, transform: LayerTransform) {
        val maxWidth = OUTPUT_SIZE * 0.88f
        val maxHeight = OUTPUT_SIZE * 0.62f
        val scale = min(maxWidth / cutout.width, maxHeight / cutout.height) * transform.scale
        val width = (cutout.width * scale).roundToInt()
        val height = (cutout.height * scale).roundToInt()
        val left = ((OUTPUT_SIZE - width) / 2f + transform.offsetX * OUTPUT_SIZE).roundToInt()
        val bottom = (OUTPUT_SIZE * (0.94f + transform.offsetY)).roundToInt()
        val destination = Rect(left, bottom - height, left + width, bottom)
        val source = Rect(0, 0, cutout.width, cutout.height)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            setShadowLayer(28f, 0f, 18f, Color.argb(150, 0, 0, 0))
        }
        canvas.drawBitmap(cutout, source, destination, shadow)
        canvas.drawBitmap(cutout, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val largestSide = maxOf(info.size.width, info.size.height)
            if (largestSide > OUTPUT_SIZE) {
                val scale = OUTPUT_SIZE.toFloat() / largestSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt(),
                    (info.size.height * scale).roundToInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private companion object {
        const val OUTPUT_SIZE = 1080
        const val BLUR_SIZE = 72
    }
}
