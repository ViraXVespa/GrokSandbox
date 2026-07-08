package com.vxv.runelitemobile.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Custom view that receives frames from the PC and renders them.
 * Currently displays test frames for development.
 */
class GameRendererView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
    }

    private var lastFrameText: String = "No frame yet"

    init {
        holder.addCallback(this)
    }

    fun onFrameReceived(frameData: ByteArray) {
        lastFrameText = String(frameData)
        drawFrame()
    }

    private fun drawFrame() {
        val canvas: Canvas? = holder.lockCanvas()
        canvas?.let {
            it.drawColor(Color.BLACK)
            it.drawText(lastFrameText, 100f, 200f, paint)
            holder.unlockCanvasAndPost(it)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
}