package com.vxv.runelitemobile.ui;

import lombok.extern.slf4j.Slf4j;

/**
 * Adapts the RuneLite client UI for mobile remote sessions.
 * Directly supports Goal #1 (hide sidebar/topbar) and Goal #4 (rescaling).
 *
 * TODO:
 * - On mobile session start: hide sidebar panel, top bar, other desktop chrome via reflection or config
 * - Apply UI scale factor sent from Android app
 * - Restore original UI state on session end
 * - Possibly force certain overlays to mobile-friendly positions/sizes
 */
@Slf4j
public class MobileUIAdapter {

    public void onMobileSessionStarted() {
        log.info("Mobile remote session started - adapting UI...");
        // TODO: Use client.getSidebarPanel().setVisible(false) or equivalent reflection
        // TODO: Adjust any scale configs or overlay positions
    }

    public void onMobileSessionEnded() {
        log.info("Mobile remote session ended - restoring UI...");
        // TODO: Restore visibility and scales
    }

    public void applyScale(float scaleFactor) {
        // TODO: Apply to relevant RuneLite UI components
    }
}