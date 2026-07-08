package com.vxv.runelitemobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vxv.runelitemobile.input.TouchInputHandler
import com.vxv.runelitemobile.render.GameRendererView

class MainActivity : AppCompatActivity() {

    private lateinit var renderer: GameRendererView
    private lateinit var touchHandler: TouchInputHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        renderer = GameRendererView(this)
        touchHandler = TouchInputHandler(this)

        setContentView(renderer)

        // TODO: Connect to PC when ready (button or auto)
        // ConnectionManager.connect("192.168.x.x")
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // ConnectionManager.disconnect()
    }
}