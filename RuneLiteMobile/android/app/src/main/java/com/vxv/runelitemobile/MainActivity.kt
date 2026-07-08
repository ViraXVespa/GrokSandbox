package com.vxv.runelitemobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * MainActivity for the RuneLiteMobile Android client.
 *
 * This is the phone-side entry point for the hybrid experience.
 * Goals from project vision:
 * - Natural touch input (swipes for camera rotate, pinch for zoom)
 * - Receive and render game frames from PC plugin with low latency
 * - Provide mobile-friendly access to RuneLite settings
 * - Clean view without desktop RuneLite UI elements (handled server-side + client rendering)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO (MVP): Replace this placeholder with a custom view that can render streamed game frames
        // (SurfaceView, GLSurfaceView, or TextureView + decoder for MJPEG / future efficient format)
        val placeholder = TextView(this).apply {
            text = "RuneLiteMobile\n\nConnecting to PC...\n\n" +
                    "Touch gestures will control the game.\n" +
                    "(Swipe = camera rotate, Pinch = zoom, Tap = click)"
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
        setContentView(placeholder)

        // TODO: Initialize connection manager (WebSocket client to plugin server on PC)
        // connectToRuneLitePlugin("192.168.x.x", port)

        // TODO: Set up gesture detectors for natural touch controls
        setupGestureDetectors()

        // TODO: Start listening for incoming frames and render them
        // startFrameReceiverAndRenderer()
    }

    private fun setupGestureDetectors() {
        // TODO: Implement GestureDetector for single taps, long-press, drags
        // TODO: Implement ScaleGestureDetector for pinch-to-zoom
        // Map normalized deltas back to PC (send via connection)
        // Example mapping:
        //   - Horizontal/vertical swipe delta → simulate mouse drag or camera yaw/pitch
        //   - Pinch scale factor → zoom in/out command or mouse wheel
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO: Delegate to both gesture detectors and handle combined gestures
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }

    // TODO: Methods to send input events to the plugin
    // private fun sendTouchEvent(x: Float, y: Float, action: String)
    // private fun sendCameraControl(deltaYaw: Float, deltaPitch: Float)
    // private fun sendZoom(delta: Float)

    // TODO: Live settings UI entry point (button or gesture to open mobile-friendly config screens)
    // These will sync with RuneLite's ConfigManager via the plugin bridge
}