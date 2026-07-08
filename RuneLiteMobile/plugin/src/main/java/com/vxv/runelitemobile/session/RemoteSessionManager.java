 // ... existing code with improvements ...

    private boolean shouldCaptureFrames() {
        return !connectedClients.isEmpty();
    }

    // Only trigger capture when clients exist
