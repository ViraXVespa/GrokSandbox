package com.vxv.runelitemobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Configuration for the RuneLiteMobile plugin.
 * Includes settings relevant to mobile remote sessions.
 */
@ConfigGroup("runelitemobile")
public interface RuneLiteMobileConfig extends Config {

    @ConfigItem(
        keyName = "enableMobileRemote",
        name = "Enable Mobile Remote",
        description = "Allow Android app connections for remote play"
    )
    default boolean enableMobileRemote() {
        return true;
    }

    @ConfigItem(
        keyName = "mobileUIScale",
        name = "Mobile UI Scale",
        description = "Scale factor applied when mobile session is active (0.5 - 2.0)"
    )
    @Range(min = 50, max = 200)
    default int mobileUIScale() {
        return 100; // percent
    }

    @ConfigItem(
        keyName = "remoteServerPort",
        name = "Remote Server Port",
        description = "Local port the plugin listens on for Android connections"
    )
    default int remoteServerPort() {
        return 8081;
    }
}