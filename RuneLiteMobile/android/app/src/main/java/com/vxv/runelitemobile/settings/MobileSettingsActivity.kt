package com.vxv.runelitemobile.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Mobile settings UI.
 * Future: Full Jetpack Compose implementation with live sync to plugin config.
 */
class MobileSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Compose UI with sections for Graphics, Overlays, Plugin settings, etc.
        // Load current values via ConnectionManager, push changes live
    }
}