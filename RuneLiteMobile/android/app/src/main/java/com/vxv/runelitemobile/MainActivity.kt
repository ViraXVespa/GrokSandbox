package com.vxv.runelitemobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vxv.runelitemobile.input.TouchInputHandler
import com.vxv.runelitemobile.render.GameRendererView

class MainActivity : AppCompatActivity() {

    private lateinit var rendererView: GameRendererView
    private lateinit var touchHandler: TouchInputHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rendererView = GameRendererView(this)
        touchHandler = TouchInputHandler(this)

        // TODO: Set rendererView as content view (or wrap it with settings button, etc.)
        setContentView(rendererView)

        // The TouchInputHandler will send messages via ConnectionManager
        // rendererView will receive frames
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event) || super.onTouchEvent(event)
    }
}