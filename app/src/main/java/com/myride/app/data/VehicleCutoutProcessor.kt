package com.myride.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.roundToInt

class VehicleCutoutProcessor(private val resolver: ContentResolver) : AutoCloseable {
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .enableForegroundConfidenceMask()
            .build(),
    )

    suspend fun cutOut(uri: Uri, maxSide: Int, primaryOnly: Boolean = false): Bitmap {
        val source = withContext(Dispatchers.IO) { decode(uri, maxSide) }
        val foreground = try {
            val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
            result.foregroundConfidenceMask
                ?.takeIf { it.capacity() >= source.width * source.height }
                ?.let { mask -> withContext(Dispatchers.Default) { refineEdges(source, mask) } }
                ?: result.foregroundBitmap
                ?: error("Не удалось найти автомобиль на фотографии")
        } finally {
            source.recycle()
        }
        return if (primaryOnly) {
            withContext(Dispatchers.Default) {
                try {
                    keepLargestArea(foreground)
                } finally {
                    foreground.recycle()
                }
            }
        } else {
            foreground
        }
    }

    private fun refineEdges(source: Bitmap, confidence: FloatBuffer): Bitmap {
        val width = source.width
        val height = source.height
        val size = width * height
        val mask = FloatArray(size)
        confidence.duplicate().apply { rewind() }.get(mask)

        // Небольшое гауссово сглаживание убирает «лесенку», а smoothstep
        // возвращает контуру чёткость без тёмного ореола вокруг автомобиля.
        val horizontal = FloatArray(size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                horizontal[index] =
                    mask[row + maxOf(0, x - 1)] * 0.25f +
                        mask[index] * 0.5f +
                        mask[row + minOf(width - 1, x + 1)] * 0.25f
            }
        }
        for (y in 0 until height) {
            val previous = maxOf(0, y - 1) * width
            val current = y * width
            val next = minOf(height - 1, y + 1) * width
            for (x in 0 until width) {
                mask[current + x] =
                    horizontal[previous + x] * 0.25f +
                        horizontal[current + x] * 0.5f +
                        horizontal[next + x] * 0.25f
            }
        }

        val pixels = IntArray(size)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (index in pixels.indices) {
            val normalized = ((mask[index] - EDGE_LOW) / (EDGE_HIGH - EDGE_LOW)).coerceIn(0f, 1f)
            val smooth = normalized * normalized * (3f - 2f * normalized)
            val alpha = (smooth * 255f).roundToInt()
            if (alpha < MIN_VISIBLE_ALPHA) {
                pixels[index] = Color.TRANSPARENT
            } else {
                pixels[index] = pixels[index].let { color ->
                    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                }
                val x = index % width
                val y = index / width
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        check(maxX >= minX && maxY >= minY) { "Не удалось найти автомобиль на фотографии" }

        val full = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val left = maxOf(0, minX - CROP_PADDING)
        val top = maxOf(0, minY - CROP_PADDING)
        val right = minOf(width - 1, maxX + CROP_PADDING)
        val bottom = minOf(height - 1, maxY + CROP_PADDING)
        val cropped = Bitmap.createBitmap(full, left, top, right - left + 1, bottom - top + 1)
        if (cropped !== full) full.recycle()
        return cropped
    }

    private fun keepLargestArea(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val size = width * height
        val pixels = IntArray(size)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val visited = BooleanArray(size)
        val queue = IntArray(size)
        var bestSeed = -1
        var bestSize = 0

        for (seed in pixels.indices) {
            if (visited[seed] || pixels[seed].ushr(24) < ALPHA_THRESHOLD) continue
            var head = 0
            var tail = 0
            var componentSize = 0
            queue[tail++] = seed
            visited[seed] = true
            while (head < tail) {
                val index = queue[head++]
                componentSize++
                val x = index % width
                if (x > 0) tail = enqueue(index - 1, pixels, visited, queue, tail)
                if (x + 1 < width) tail = enqueue(index + 1, pixels, visited, queue, tail)
                if (index >= width) tail = enqueue(index - width, pixels, visited, queue, tail)
                if (index + width < size) tail = enqueue(index + width, pixels, visited, queue, tail)
            }
            if (componentSize > bestSize) {
                bestSize = componentSize
                bestSeed = seed
            }
        }
        check(bestSeed >= 0) { "Не удалось выделить главный объект" }

        val keep = BooleanArray(size)
        var head = 0
        var tail = 0
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        queue[tail++] = bestSeed
        keep[bestSeed] = true
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            if (x > 0) tail = enqueue(index - 1, pixels, keep, queue, tail)
            if (x + 1 < width) tail = enqueue(index + 1, pixels, keep, queue, tail)
            if (index >= width) tail = enqueue(index - width, pixels, keep, queue, tail)
            if (index + width < size) tail = enqueue(index + width, pixels, keep, queue, tail)
        }
        pixels.indices.forEach { if (!keep[it]) pixels[it] = Color.TRANSPARENT }
        val isolated = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val cropped = Bitmap.createBitmap(isolated, minX, minY, maxX - minX + 1, maxY - minY + 1)
        if (cropped !== isolated) isolated.recycle()
        return cropped
    }

    private fun enqueue(
        index: Int,
        pixels: IntArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
    ): Int {
        if (visited[index] || pixels[index].ushr(24) < ALPHA_THRESHOLD) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }

    private fun decode(uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val largestSide = maxOf(info.size.width, info.size.height)
            if (largestSide > maxSide) {
                val scale = maxSide.toFloat() / largestSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt(),
                    (info.size.height * scale).roundToInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    override fun close() = segmenter.close()

    private companion object {
        const val ALPHA_THRESHOLD = 32
        const val EDGE_LOW = 0.34f
        const val EDGE_HIGH = 0.66f
        const val MIN_VISIBLE_ALPHA = 4
        const val CROP_PADDING = 6
    }
}
