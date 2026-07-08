package com.vxv.runelitemobile.input

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

import com.vxv.runelitemobile.connection.ConnectionManager

/**
 * Handles touch gestures and converts them to messages sent to the PC plugin.
 */
class TouchInputHandler(context: Context) {

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val msg = "TAP:${e.x},${e.y}"
            ConnectionManager.sendMessage(msg)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            // Treat scroll/drag as camera swipe for MVP
            val msg = "SWIPE_CAMERA:${distanceX},${distanceY}"
            ConnectionManager.sendMessage(msg)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val msg = "LONG_PRESS:${e.x},${e.y}"
            ConnectionManager.sendMessage(msg)
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val msg = "PINCH_SCALE:${detector.scaleFactor}"
            ConnectionManager.sendMessage(msg)
            return true
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled
    }
}