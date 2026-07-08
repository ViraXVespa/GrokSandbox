 // ... existing code ...

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message.startsWith("SETTINGS_REQUEST")) {
            // TODO: Send current config back to client
            return;
        }

        if (message.startsWith("CONFIG_UPDATE")) {
            // TODO: Apply incoming config changes
            return;
        }

        InputEvent event = parseInputMessage(message);
        if (event != null) {
            inputInjector.handleEvent(event);
        }
    }

    // ... rest of existing code ...