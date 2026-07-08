package com.vxv.runelitemobile.input;

/**
 * Represents a normalized input event coming from the Android client.
 */
public class InputEvent {

    public enum Type {
        TAP,
        LONG_PRESS,
        DRAG_START,
        DRAG_MOVE,
        DRAG_END,
        PINCH_START,
        PINCH_SCALE,
        PINCH_END,
        SWIPE_CAMERA,
        ZOOM
    }

    public final Type type;
    public final float x;
    public final float y;
    public final float deltaX;
    public final float deltaY;
    public final float scale;

    public InputEvent(Type type, float x, float y, float deltaX, float deltaY, float scale) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.scale = scale;
    }
}