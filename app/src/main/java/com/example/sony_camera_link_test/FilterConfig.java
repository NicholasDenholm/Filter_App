package com.example.sony_camera_link_test;

public class FilterConfig {

    private int intensity;
    private Enum<?> variant;

    public void setIntensity(int intensity) {
        this.intensity = intensity;
    }

    public void setVariant(Enum<?> variant) {
        this.variant = variant;
    }

    public FilterConfig(int intensity, Enum<?> variant) {
        this.intensity = intensity;
        this.variant = variant;
    }

    public int getIntensity() {
        return intensity;
    }

    public Enum<?> getVariant() {
        return variant;
    }
}
