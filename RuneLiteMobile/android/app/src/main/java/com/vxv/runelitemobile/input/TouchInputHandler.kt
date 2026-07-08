package com.vxv.runelitemobile.input

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

import com.vxv.runelitemobile.connection.ConnectionManager

/**
 * Translates Android touch gestures into normalized InputEvent objects
 * and sends them to the PC via ConnectionManager.
 *
 * Directly implements Goal #2: natural touch input (swipes, pinch, taps).
 */
class TouchInputHandler {

    // TODO: Hold references to GestureDetector and ScaleGestureDetector

    fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO: Let detectors process the event
        // When a gesture is recognized, create InputEvent and send:
        // ConnectionManager.sendInputEvent(...)
        return true
    }

    // Example mapping ideas (to be implemented):
    // - Single finger drag → DRAG_MOVE or SWIPE_CAMERA (deltaX/deltaY normalized)
    // - Pinch → PINCH_SCALE with scale factor
    // - Tap → TAP at (x, y)
    // - Long press → LONG_PRESS
}