package com.vxv.runelitemobile.render

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Custom view responsible for receiving game frames from the PC plugin
 * and rendering them with low latency.
 *
 * TODO (core for Goal #2 - natural touch + visual fidelity):
 * - Implement MJPEG or custom decoder
 * - Efficient bitmap drawing on SurfaceView
 * - Handle aspect ratio / scaling to phone screen
 * - Overlay touch input layer on top
 * - Stats overlay (FPS, latency) for debugging
 */
class GameRendererView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // TODO: Start frame receiving thread / coroutine
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // TODO: Handle resize, notify plugin of new resolution if needed
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // TODO: Stop frame receiver
    }

    // TODO: Method called when new frame bytes arrive from connection
    // fun onNewFrame(frameData: ByteArray) { ... decode and draw on canvas }
}