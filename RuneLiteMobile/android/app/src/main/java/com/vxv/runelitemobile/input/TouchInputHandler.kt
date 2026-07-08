package com.vxv.runelitemobile.input

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

import com.vxv.runelitemobile.connection.ConnectionManager

/**
 * Converts Android touch gestures into protocol messages sent to the plugin.
 */
class TouchInputHandler(context: Context) {

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            ConnectionManager.sendMessage("TAP:${e.x},${e.y}")
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            ConnectionManager.sendMessage("LONG_PRESS:${e.x},${e.y}")
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            ConnectionManager.sendMessage("SWIPE_CAMERA:${distanceX},${distanceY}")
            return true
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            ConnectionManager.sendMessage("PINCH_SCALE:${detector.scaleFactor}")
            return true
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled
    }
}