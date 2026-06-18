package com.example.sony_camera_link_test;

public enum FilterInfo {
    K_MEANS(
            "K-Means",
            "Clusters image colors into a distinct, optimized color palette using spatial vector quantization."
    ),
    PIXELATE(
            "Pixelate",
            "Downsamples the visual resolution into blocky pixel clusters for a retro, low-fidelity aesthetic."
    ),
    GRAYSCALE(
            "Grayscale",
            "Converts RGB channels into monochromatic luminance values, completely stripping color saturation."
    ),
    INTERLACED(
            "Interlaced",
            "Combines alternating row exposures or sequence frames to create high-contrast composite imagery."
    ),
    FLOYD_STEINBERG(
            "FloydSteinbergDithering",
            "Applies an error-diffusion algorithm to approximate color gradients using a highly restricted palette."
    ),
    COLOUR_BLIND(
            "ColourBlind",
            "Transforms the active color spectrum to simulate or adjust layout visibility for color-deficiencies."
    );


    private final String title;
    private final String description;

    FilterInfo(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
}