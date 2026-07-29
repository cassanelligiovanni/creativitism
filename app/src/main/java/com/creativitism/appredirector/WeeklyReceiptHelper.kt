package com.creativitism.appredirector

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

object WeeklyReceiptHelper {

    const val FILE_PROVIDER_AUTHORITY = "com.creativitism.appredirector.fileprovider"

    fun weekId(date: LocalDate): String {
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        return "%d-W%02d".format(year, week)
    }

    fun previousWeekId(today: LocalDate = LocalDate.now()): String =
        weekId(today.minusWeeks(1))

    fun eventsInWeek(
        events: List<InterventionEvent>,
        targetWeekId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<InterventionEvent> =
        events.filter { weekId(localDate(it.timestamp, zoneId)) == targetWeekId }

    fun shouldShowReceipt(
        lastReceiptWeekId: String?,
        previousWeekId: String,
        priorWeekEvents: List<InterventionEvent>,
    ): Boolean =
        lastReceiptWeekId != previousWeekId && priorWeekEvents.isNotEmpty()

    fun receiptCopy(reclaimedMillis: Long): String {
        val reclaimed = formatDuration(reclaimedMillis)
        return "This week you traded $reclaimed of scrolling for time in your Creative apps."
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
            hours > 0L -> "${hours}h"
            minutes == 1L -> "1 minute"
            else -> "$minutes minutes"
        }
    }

    /**
     * Renders [copy] to a PNG under cache/receipts/ and returns an ACTION_SEND
     * intent with a FileProvider content URI (never file://).
     */
    fun shareReceiptIntent(context: Context, copy: String): Intent {
        val bitmap = renderReceiptBitmap(copy)
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "weekly-receipt-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, copy)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun renderReceiptBitmap(copy: String): Bitmap {
        val width = 1080
        val horizontalPadding = 72
        val verticalPadding = 96
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF9F9F9.toInt()
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF9F9F9.toInt()
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val accentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5FC992.toInt()
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textWidth = width - horizontalPadding * 2
        val bodyLayout = StaticLayout.Builder
            .obtain(copy, 0, copy.length, bodyPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()

        val brandHeight = brandPaint.fontSpacing
        val height = (verticalPadding * 2 + brandHeight + 48 + bodyLayout.height).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF07080A.toInt())

        var y = verticalPadding + brandPaint.textSize
        canvas.drawText("Creativitism", horizontalPadding.toFloat(), y, brandPaint)
        val brandWidth = brandPaint.measureText("Creativitism")
        canvas.drawText("~", horizontalPadding + brandWidth, y, accentPaint)

        y += 48f
        canvas.save()
        canvas.translate(horizontalPadding.toFloat(), y)
        bodyLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun localDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
