package com.example.sony_camera_link_test;

public class CameraOption {
    final String label;
    final String logicalId;
    final int facing;
    final boolean isSystemFallback;

    // Constructor for real internal cameras
    CameraOption(String label, String logicalId, int facing) {
        this.label = label;
        this.logicalId = logicalId;
        this.facing = facing;
        this.isSystemFallback = false;
    }

    // Constructor for the "System Camera" escape hatch
    CameraOption(String label) {
        this.label = label;
        this.logicalId = "";
        this.facing = -1;
        this.isSystemFallback = true;
    }

    // The Spinner uses this method to decide what text to show on screen
    @Override
    public String toString() {
        return label;
    }
}
