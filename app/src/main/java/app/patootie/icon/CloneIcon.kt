package app.patootie.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

/** Tints a clone's icon can wear. null = keep the app's own colors. */
object Tints {
    val all: List<Pair<String, Int?>> = listOf(
        "Plain" to null,
        "Lavender" to 0xFFB79CED.toInt(),
        "Mint" to 0xFF7FD8B5.toInt(),
        "Bubblegum" to 0xFFFF9CC8.toInt(),
        "Peach" to 0xFFFFB38A.toInt(),
        "Sky" to 0xFF8EC9FF.toInt(),
        "Lemon" to 0xFFFFE07A.toInt(),
    )
}

object Stickers {
    val all: List<String?> = listOf(null, "💖", "⭐", "🎀", "🌸", "🍓", "🐰", "🐱", "☁️", "🌈", "🍡", "🧁", "✨", "🍀")
}

/**
 * Draws a clone's icon: the original app icon, washed with the tint (keeping the
 * icon's shape and shading), with the sticker in a white bubble at the corner.
 * Used both in the app and for home-screen shortcuts, so they always match.
 */
object CloneIconRenderer {
    fun render(appIcon: Drawable, tint: Int?, sticker: String?, size: Int = 192): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val inset = if (sticker != null) size / 14 else 0
        // The icon drawable is shared through a cache; draw a private copy.
        val icon = appIcon.constantState?.newDrawable()?.mutate() ?: appIcon
        icon.setBounds(inset, inset, size - inset, size - inset)
        icon.draw(canvas)

        if (tint != null) {
            // SRC_ATOP only paints where the icon already is, so the shape survives.
            val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tint
                alpha = 115
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), wash)
        }

        if (sticker != null) {
            val r = size * 0.21f
            val cx = size - r - size * 0.01f
            val cy = size - r - size * 0.01f
            canvas.drawCircle(cx, cy + r * 0.06f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
            canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = r * 1.15f
                textAlign = Paint.Align.CENTER
            }
            val baseline = cy - (text.descent() + text.ascent()) / 2
            canvas.drawText(sticker, cx, baseline, text)
        }
        return bitmap
    }
}
