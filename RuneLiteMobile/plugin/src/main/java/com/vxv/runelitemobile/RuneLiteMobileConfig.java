package com.vxv.runelitemobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Configuration options for the RuneLiteMobile plugin.
 */
@ConfigGroup("runelitemobile")
public interface RuneLiteMobileConfig extends Config {

    @ConfigItem(
        keyName = "enableMobileRemote",
        name = "Enable Mobile Remote",
        description = "Allow connections from the Android app"
    )
    default boolean enableMobileRemote() {
        return true;
    }

    @ConfigItem(
        keyName = "mobileUIScale",
        name = "Mobile UI Scale",
        description = "UI scale factor to apply during mobile sessions (50-200%)"
    )
    @Range(min = 50, max = 200)
    default int mobileUIScale() {
        return 100;
    }

    @ConfigItem(
        keyName = "remoteServerPort",
        name = "Remote Server Port",
        description = "Port the plugin listens on for Android connections"
    )
    default int remoteServerPort() {
        return 8081;
    }
}