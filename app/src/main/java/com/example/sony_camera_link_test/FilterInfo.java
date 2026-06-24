package com.example.sony_camera_link_test;

public enum FilterInfo {
    K_MEANS(
            "K-Means",
            "Clusters image colors into a number of distinct groups depending on the cluster count.",

            new String[]{
                    "Lower cluster count: More pastelly or two-tone, looses colour depth \n\n" +
                    "Higher cluster count: Closer to normal, retains colour depth."
            }
    ),
    PIXELATE(
            "Pixelate",
            "Downsamples the visual resolution into blocky pixel clusters for a retro, low-fidelity aesthetic.",
            new String[]{
                    "Lower strength: Closer to normal, keeps details \n\n" +
                    "Higher strength: Very blocky, looses all detail."
            }
    ),
    INTERLACED(
            "Interlaced",
            "Combines alternating row exposures or sequence frames to create composite images.",
            new String[] {
                    "-Checkered: Horizontal checkered pattern.",
                    "-Vertical Stripes: Vertical stripped pattern.",
                    "-Half/Hald: First photo on the left, second on the right.",
                    "-Noise: Each pixel is randomly assigned to the 1st or 2nd photo.",
                    "-Swirl: Photos are assigned to pixels in a radial pattern starting from the center.",
                    "-Grid: Triangle checkered pattern."
            }
    ),
    FLOYD_STEINBERG(
            "FloydSteinbergDithering",
            "Applies an error-diffusion algorithm, that creates a pixelation sort of effect.",
            new String[]{
                    "-FloydSteinberg A: With colour ",
                    "-FloydSteinberg A: Black and white",
                    "-Glitchy Dither: Colourful glitchy",
                    "-Deep Fried: Blown out very red ",
                    "-Spooky Dark Dither: Black and white, blown out."
            }
    ),
    COLOUR_BLIND(
            "ColourBlind",
            "Transforms the active color spectrum to simulate or adjust layout visibility for color-deficiencies.",
            new String[]{
                    "Protanopia: Red-blind simulation.",
                    "Deuteranopia: Green-blind simulation.",
                    "Tritanopia: Blue-blind simulation.",
                    "Dog Vision: Red shifted toward green.",
                    "Grayscale: Black and white."
            }
    );

    private final String title;
    private final String description;
    private String[] subfilter;

    FilterInfo(String title, String description, String subfilter[]) {
        this.title = title;
        this.description = description;
        this.subfilter = subfilter;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSubFilter(int option) { return subfilter[option]; }
}
