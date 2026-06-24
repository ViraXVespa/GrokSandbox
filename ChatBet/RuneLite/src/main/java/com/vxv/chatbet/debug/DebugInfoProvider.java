package com.vxv.chatbet.debug;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Modules (and ChatBetPlugin) can implement this interface to explicitly expose
 * the variables they want to appear in the live debug panel.
 *
 * This is preferred over raw reflection because:
 * - It is explicit and intentional
 * - It allows safe unloading of modules (no hidden references)
 * - It supports lazy evaluation via Supplier
 * - It gives clean, human-readable labels
 */
public interface DebugInfoProvider {

    /**
     * Returns a map of human-readable labels to suppliers that provide the current value.
     * The debug panel will invoke these suppliers when refreshing.
     */
    Map<String, Supplier<Object>> getDebugVariables();
}