package com.vxv.runelitemobile.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Renders frames received from the PC plugin.
 * Supports both test frames and real PNG data.
 */
class GameRendererView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var currentBitmap: Bitmap? = null

    init {
        holder.addCallback(this)
    }

    fun onFrameReceived(frameData: ByteArray) {
        try {
            // Try to decode as PNG first
            val bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.size)
            if (bitmap != null) {
                currentBitmap = bitmap
            } else {
                // Fallback: treat as text/test frame
                // For now just log
            }
            drawCurrentFrame()
        } catch (e: Exception) {
            // Ignore decode errors for now
        }
    }

    private fun drawCurrentFrame() {
        val canvas: Canvas = holder.lockCanvas() ?: return

        canvas.drawColor(Color.BLACK)

        currentBitmap?.let { bitmap ->
            // Simple scaling to fit view
            val scale = minOf(
                width.toFloat() / bitmap.width,
                height.toFloat() / bitmap.height
            )
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            val left = (width - newWidth) / 2f
            val top = (height - newHeight) / 2f

            canvas.drawBitmap(scaledBitmap, left, top, null)
        }

        holder.unlockCanvasAndPost(canvas)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawCurrentFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
}