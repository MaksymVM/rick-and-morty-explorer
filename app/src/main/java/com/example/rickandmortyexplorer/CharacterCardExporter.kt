package com.example.rickandmortyexplorer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CharacterCardExporter {

    fun requiredPermission(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> Manifest.permission.WRITE_EXTERNAL_STORAGE
        else -> Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            requiredPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun saveCharacterPosterToGallery(
        context: Context,
        character: Character
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = renderPosterBitmap(character)
            val safeName = character.name
                .replace(Regex("[^A-Za-zА-Яа-яІіЇїЄєҐґ0-9]+"), "_")
                .trim('_')
                .ifEmpty { "character" }
            val fileName = "rick_and_morty_${character.id}_${safeName}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/RickAndMorty"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Не вдалося створити файл у Галереї")

                resolver.openOutputStream(uri)?.use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        error("Не вдалося записати PNG")
                    }
                } ?: error("Не вдалося відкрити потік запису")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val albumDir = File(picturesDir, "RickAndMorty")
                if (!albumDir.exists()) {
                    albumDir.mkdirs()
                }
                val outFile = File(albumDir, fileName)
                FileOutputStream(outFile).use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        error("Не вдалося записати PNG")
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
            }

            val totalSaved = countSavedCardsInGallery(context)
            bitmap.recycle()
            if (totalSaved > 0) {
                "Картку збережено в Галерею (альбом RickAndMorty, усього карток: $totalSaved)"
            } else {
                "Картку збережено в Галерею (Pictures/RickAndMorty)"
            }
        }
    }

    private fun countSavedCardsInGallery(context: Context): Int {
        return runCatching {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("rick_and_morty_%")
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor -> cursor.count } ?: 0
        }.getOrDefault(0)
    }

    private fun renderPosterBitmap(character: Character): Bitmap {
        val width = 900
        val height = 1220
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Тло постера
        canvas.drawColor(Color.parseColor("#0F172A"))

        val statusAccent = when (character.status) {
            "Alive" -> Color.parseColor("#4CAF50")
            "Dead" -> Color.parseColor("#EF5350")
            else -> Color.parseColor("#94A3B8")
        }

        // Зовнішня картка
        val cardRect = RectF(48f, 48f, width - 48f, height - 48f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, 44f, 44f, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusAccent
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(cardRect, 44f, 44f, borderPaint)

        // Верхній бейдж досьє
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("RICK & MORTY EXPLORER • ДОСЬЄ #${character.id}", width / 2f, 125f, headerPaint)

        // Круглий аватар з ініціалами персонажа
        val avatarCenterY = 280f
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(width / 2f, avatarCenterY, 105f, avatarPaint)
        canvas.drawCircle(width / 2f, avatarCenterY, 105f, borderPaint)

        val initials = character.name
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "?" }

        val initialsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 74f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(initials, width / 2f, avatarCenterY + 26f, initialsPaint)

        // Ім'я персонажа
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val clippedName = TextUtils.ellipsize(
            character.name,
            namePaint,
            width - 160f,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(clippedName, width / 2f, 455f, namePaint)

        // Плашка статусу
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusAccent
            style = Paint.Style.FILL
        }
        val statusTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val statusLabel = "СТАТУС: ${character.status.uppercase()}"
        val pillWidth = statusTextPaint.measureText(statusLabel) + 72f
        val pillRect = RectF(
            (width - pillWidth) / 2f,
            485f,
            (width + pillWidth) / 2f,
            545f
        )
        canvas.drawRoundRect(pillRect, 30f, 30f, pillPaint)
        canvas.drawText(statusLabel, width / 2f, 525f, statusTextPaint)

        // Інформаційні рядки досьє
        val rows = listOf(
            "Вид (Species)" to character.species,
            "Стать (Gender)" to character.gender,
            "Походження (Origin)" to character.origin,
            "Остання відома локація" to character.location,
            "Кількість епізодів" to "${character.episodeCount} серій"
        )

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 26f
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            strokeWidth = 2f
        }

        var currentY = 620f
        val leftX = 105f
        val maxTextWidth = width - 210f

        rows.forEachIndexed { index, (label, value) ->
            canvas.drawText(label, leftX, currentY, labelPaint)
            val clippedValue = TextUtils.ellipsize(
                value,
                valuePaint,
                maxTextWidth,
                TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(clippedValue, leftX, currentY + 44f, valuePaint)
            if (index < rows.lastIndex) {
                canvas.drawLine(leftX, currentY + 68f, width - leftX, currentY + 68f, dividerPaint)
            }
            currentY += 110f
        }

        return bitmap
    }
}
