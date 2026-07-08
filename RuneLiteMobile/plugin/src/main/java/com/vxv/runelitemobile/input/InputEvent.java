package com.vxv.runelitemobile.input;

public class InputEvent {

    public enum Type {
        TAP, LONG_PRESS, DRAG_START, DRAG_MOVE, DRAG_END,
        PINCH_START, PINCH_SCALE, PINCH_END,
        SWIPE_CAMERA, ZOOM,
        SETTINGS_REQUEST, CONFIG_UPDATE
    }

    public final Type type;
    public final float x, y, deltaX, deltaY, scale;

    public InputEvent(Type type, float x, float y, float deltaX, float deltaY, float scale) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.scale = scale;
    }
}