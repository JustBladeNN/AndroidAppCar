package com.myride.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToInt

class StickerExporter(private val context: Context) {
    private val outputDir = File(context.cacheDir, "shared").apply { mkdirs() }
    private val downloads = DownloadStore(context)

    fun stickers(bitmaps: List<Bitmap>): List<Uri> {
        val batchId = System.currentTimeMillis()
        return bitmaps.mapIndexed { index, bitmap ->
            val file = File(outputDir, "myride_sticker_${batchId}_$index.png")
            FileOutputStream(file).use { writeSticker(bitmap, it) }
            file.uri()
        }
    }

    fun gif(bitmaps: List<Bitmap>): List<Uri> {
        val file = File(outputDir, "myride_${System.currentTimeMillis()}.gif")
        FileOutputStream(file).use { writeGif(bitmaps, it) }
        return listOf(file.uri())
    }

    fun saveStickers(bitmaps: List<Bitmap>): List<Uri> {
        val batchId = System.currentTimeMillis()
        return bitmaps.mapIndexed { index, bitmap ->
            downloads.save("myride_sticker_${batchId}_$index.png", "image/png") { writeSticker(bitmap, it) }
        }
    }

    fun saveGif(bitmaps: List<Bitmap>): Uri =
        downloads.save("myride_${System.currentTimeMillis()}.gif", "image/gif") { writeGif(bitmaps, it) }

    private fun writeSticker(bitmap: Bitmap, output: OutputStream) {
        check(squareFrame(bitmap, Color.TRANSPARENT).compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "Не удалось записать PNG"
        }
    }

    private fun writeGif(bitmaps: List<Bitmap>, output: OutputStream) {
        val encoder = GifEncoder(output, OUTPUT_SIZE, OUTPUT_SIZE, 0)
        val options = ImageOptions().apply { setDelay(650, TimeUnit.MILLISECONDS) }
        bitmaps.forEach { bitmap -> encoder.addImage(squareFrame(bitmap, GIF_BACKGROUND).toRgb(), options) }
        encoder.finishEncoding()
    }

    private fun squareFrame(source: Bitmap, background: Int): Bitmap {
        val result = createBitmap(OUTPUT_SIZE, OUTPUT_SIZE)
        val canvas = Canvas(result)
        canvas.drawColor(background)

        val maxContent = OUTPUT_SIZE - PADDING * 2
        val scale = min(maxContent.toFloat() / source.width, maxContent.toFloat() / source.height)
        val width = (source.width * scale).roundToInt()
        val height = (source.height * scale).roundToInt()
        val left = (OUTPUT_SIZE - width) / 2
        val top = (OUTPUT_SIZE - height) / 2
        canvas.drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return result
    }

    private fun Bitmap.toRgb(): Array<IntArray> = Array(height) { y ->
        IntArray(width).also { row -> getPixels(row, 0, width, 0, y, width, 1) }
    }

    private fun File.uri(): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", this)

    private companion object {
        const val OUTPUT_SIZE = 512
        const val PADDING = 24
        val GIF_BACKGROUND = Color.rgb(10, 13, 18)
    }
}
