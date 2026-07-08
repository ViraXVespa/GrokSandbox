package com.vxv.runelitemobile.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Mobile-friendly settings UI for RuneLite + plugin configuration.
 * Goal #3: Access and control all RuneLite settings via a clean mobile UI.
 *
 * Future: Use Jetpack Compose for modern, touch-optimized screens
 * (tabs or bottom navigation for Overlays, Graphics, Plugin settings, etc.)
 * Live sync with the PC via ConnectionManager (request snapshot → edit → push changes)
 */
class MobileSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Set content view with Compose or traditional layout
        // TODO: Load current config snapshot from ConnectionManager
        // TODO: Provide editable fields for common RuneLite settings + plugin toggles
        // TODO: On change, send update back to plugin for live application
    }
}