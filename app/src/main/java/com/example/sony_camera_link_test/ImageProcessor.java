package com.example.sony_camera_link_test;

import static com.example.sony_camera_link_test.MathUtils.DEUTERANOPIA;
import static com.example.sony_camera_link_test.MathUtils.DOG_SIMULATION;
import static com.example.sony_camera_link_test.MathUtils.LMS_TO_RGB;
import static com.example.sony_camera_link_test.MathUtils.PROTANOPIA;
import static com.example.sony_camera_link_test.MathUtils.RGB_TO_LMS;
import static com.example.sony_camera_link_test.MathUtils.TRITANOPIA;
import static com.example.sony_camera_link_test.MathUtils.linearToSrgb;
import static com.example.sony_camera_link_test.MathUtils.multiplyMatrixAndVector;
import static com.example.sony_camera_link_test.MathUtils.srgbToLinear;
import static java.lang.Math.round;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ImageProcessor {
    // ------ Utils ----------------------------------------------------------
    public static Bitmap imageToBitmap(Bitmap src) {
        Bitmap bmp = Bitmap.createBitmap(
                src.getWidth(),
                src.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        return bmp;
    };

    public ArrayList<float[]> extractRGBValues(Bitmap bmp) {

        ArrayList<float[]> rgbValues = new ArrayList<>();

        int width = bmp.getWidth();
        int height = bmp.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int pixel = bmp.getPixel(x, y);

                float[] rgb = {
                        Color.red(pixel),
                        Color.green(pixel),
                        Color.blue(pixel)
                };

                rgbValues.add(rgb);
            }
        }

        return rgbValues;
    }

    // Broken but it causes a shearing affect, could be cool?
    public static List<float[]> extractRGBValuesME(Bitmap bmp) {
        ArrayList<float[]> rgbValues = new ArrayList<>();
        int height = bmp.getHeight();
        int width = bmp.getWidth();

        for (int x = 0; x < width ;x++) {
            for (int y = 0; y < height; y++) {
                int pixel = bmp.getPixel(x, y);

                float red = Color.red(pixel);
                float green = Color.green(pixel);
                float blue = Color.blue(pixel);

                float[] rgb = {red, green, blue};
                rgbValues.add(rgb);

            }
        }

        return rgbValues;
    };

    // ------ GreyScale ------------------------------------------------------
    public static Bitmap toGrayScale(Bitmap src) {
        Bitmap bmp = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(filter);
        canvas.drawBitmap(src, 0, 0, paint);

        //bmp = useFloydSteinbergDithering(bmp);

        return bmp;
    };

    public static int find_closest_palette_color(int pixel) {
        return (int) round((float) pixel / 255);
    }

    // ------ Dithering ------------------------------------------------------
    // TODO could also do a type of Boustrophedon transform (meandering through pixels)

    public static Bitmap createDitheringDistpacter(Bitmap src, int k) {
        switch (k % 5) {
            case 0: return dither(src);
            case 1: return dither(toGrayScale(src));
            case 2: return deepFriedEffect(src);
            case 3: return useFloydSteinbergDithering(src, 0);
            case 4: return useFloydSteinbergDithering(src, 1);
            default: return dither(src);
        }
    }

    public static int[] subtractPixels(int oldPixel, int newPixel) {
        int oldR = Color.red(oldPixel);
        int oldG = Color.green(oldPixel);
        int oldB = Color.blue(oldPixel);

        int newR = Color.red(newPixel);
        int newG = Color.green(newPixel);
        int newB = Color.blue(newPixel);

        int errR = oldR - newR;
        int errG = oldG - newG;
        int errB = oldB - newB;

        //int[] res = new int[3];
        //res[0] = errR;
        //res[1] = errG;
        //res[2] = errB;

        //return res;
        return new int[] { errR, errG, errB };
    }

    public static void addError(Bitmap bmp, int x, int y, int errR, int errG, int errB, float factor) {
        int pixel = bmp.getPixel(x, y);

        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);

        r += (int)(errR * factor);
        g += (int)(errG * factor);
        b += (int)(errB * factor);

        // Optional clamp values to 0-255
        //r = Math.max(0, Math.min(255, r));
        //g = Math.max(0, Math.min(255, g));
        //b = Math.max(0, Math.min(255, b));

        bmp.setPixel(x,y, Color.rgb(r,g,b));
    }

    public static Bitmap deepFriedEffect(Bitmap src) {
        // This is my attempt at the Floyd–Steinberg dithering effect. It is not as excpected
        // Problem was that I was editing bmp not src?
        // Algo found from wiki page: https://en.wikipedia.org/wiki/Floyd%E2%80%93Steinberg_dithering

        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap bmp = imageToBitmap(src);

        for (int y = 0; y < height- 1; y++) {
            for (int x = 1; x < width - 1 ; x++) {

                // algo needs to read modified pixels as it goes

                //int oldPixel = src.getPixel(x, y);
                int oldPixel = src.getPixel(x,y);

                //int newPixel = find_closest_palette_color(oldPixel); // Red casts everything?
                //bmp.setPixel(x, y, newPixel);
                int oldR = Color.red(oldPixel);
                int oldG = Color.green(oldPixel);
                int oldB = Color.blue(oldPixel);

                int newR = oldR < 128 ? 0 : 255;
                int newG = oldG < 128 ? 0 : 255;
                int newB = oldB < 128 ? 0 : 255;

                int newPixel = Color.rgb(newR, newG, newB);

                src.setPixel(x, y, newPixel);

                // Something is wrong with my subtractPixels method
                int errR = oldR - newR;
                int errG = oldG - newG;
                int errB = oldB - newB;

                // Cant simply subtract pixels...
                //int quant_error = oldPixel - newPixel;
                //int[] errIntArray = subtractPixels(oldPixel, newPixel);

                //int errR = errIntArray[0];
                //int errG = errIntArray[1];
                //int errB = errIntArray[2];

                // Right
                addError(bmp, x + 1, y, errR, errG, errB, 7f / 16f);

                // Bottom-left
                addError(bmp, x - 1, y + 1, errR, errG, errB, 3f / 16f);

                // Bottom
                addError(bmp, x, y + 1, errR, errG, errB, 5f / 16f);

                // Bottom-right
                addError(bmp, x + 1, y + 1, errR, errG, errB, 1f / 16f);
            }

        }

        return bmp;
    }

    public static Bitmap useFloydSteinbergDithering(Bitmap src, int option) {
        // Algo found from wiki page: https://en.wikipedia.org/wiki/Floyd%E2%80%93Steinberg_dithering

        int width = src.getWidth();
        int height = src.getHeight();
        //Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // Maybe set bmp as src copy?...
        // TODO test out this iteration of algo then maybe try copy of src
        Bitmap bmp = imageToBitmap(src);

        for (int y = 0; y < height- 1; y++) {
            for (int x = 1; x < width - 1 ; x++) {

                // algo needs to read modified pixels as it goes

                //int oldPixel = src.getPixel(x, y);
                int oldPixel = src.getPixel(x,y);
                //int newPixel = find_closest_palette_color(oldPixel); // Red casts everything?
                //bmp.setPixel(x, y, newPixel);
                int oldR = Color.red(oldPixel);
                int oldG = Color.green(oldPixel);
                int oldB = Color.blue(oldPixel);

                // DeepFried effect
                if (option == 0) {

                    int newR = oldR < 128 ? 1 : 255;
                    int newG = oldG < 128 ? 1 : 255;
                    int newB = oldB < 128 ? 1 : 205;

                    int newPixel = Color.rgb(newR, newG, newB);

                    bmp.setPixel(x, y, newPixel);

                    // Something is wrong with my subtractPixels method
                    int errR = oldR - newR;
                    int errG = oldG - newG;
                    int errB = oldB - newB;

                    // Right
                    addError(bmp, x + 1, y, errR, errG, errB, 7f / 16f);

                    // Bottom-left
                    addError(bmp, x - 1, y + 1, errR, errG, errB, 3f / 16f);

                    // Bottom
                    addError(bmp, x, y + 1, errR, errG, errB, 5f / 16f);

                    // Bottom-right
                    addError(bmp, x + 1, y + 1, errR, errG, errB, 1f / 16f);
                }

                // Glitchy Grayscale
                if (option == 1) {
                    int gray = (int) (
                            0.299f * oldR +
                                    0.587f * oldG +
                                    0.114f * oldB
                    );

                    int newGray = gray < 128 ? 0 : 255;

                    int newPixel = Color.rgb(newGray, newGray, newGray);

                    bmp.setPixel(x, y, newPixel);

                    int err = gray - newGray;

                    // Right
                    addError(bmp, x + 1, y, err, err, err, 7f / 16f);

                    // Bottom-left
                    addError(bmp, x - 1, y + 1, err, err, err, 3f / 16f);

                    // Bottom
                    addError(bmp, x, y + 1, err, err, err, 5f / 16f);

                    // Bottom-right
                    addError(bmp, x + 1, y + 1, err, err, err, 1f / 16f);
                }
                // Cant simply subtract pixels...
                //int quant_error = oldPixel - newPixel;
                //int[] errIntArray = subtractPixels(oldPixel, newPixel);

                //int errR = errIntArray[0];
                //int errG = errIntArray[1];
                //int errB = errIntArray[2];



                /*
                int pixelToRight = src.getPixel(x + 1, y) + (quant_error * (7f / 16f));
                int pixelBelowToRight = src.getPixel(x + 1, y + 1) + (quant_error * (1f / 16f));
                int pixelBelow = src.getPixel(x, y + 1) + (quant_error * (5f / 16f));
                int pixelBelowToLeft = src.getPixel(x - 1, y + 1) + (quant_error * (3f / 16f));

                bmp.setPixel(x+1, y, pixelToRight);
                bmp.setPixel(x+1, y+1, pixelBelowToRight);
                bmp.setPixel(x, y+1, pixelBelow);
                bmp.setPixel(x-1, y-1, pixelBelowToLeft);
                */

            }

        }

        return bmp;
    }

    // This and distribute error actually work as intented...
    public static Bitmap dither(Bitmap src) {

        int width = src.getWidth();
        int height = src.getHeight();

        // Copy source bitmap so we don't modify original
        Bitmap bmp = src.copy(Bitmap.Config.ARGB_8888, true);

        // Pull all pixels into memory at once
        int[] pixels = new int[width * height];

        bmp.getPixels(pixels, 0, width, 0, 0, width, height);

        // Main loop
        // Skip outer border to eliminate bounds checks
        for (int y = 0; y < height - 1; y++) {

            int row = y * width;
            int nextRow = row + width;

            for (int x = 1; x < width - 1; x++) {

                int i = row + x;

                int pixel = pixels[i];

                // Fast channel extraction
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Quantize
                int newR = (r < 128) ? 0 : 255;
                int newG = (g < 128) ? 0 : 255;
                int newB = (b < 128) ? 0 : 255;

                // Write quantized pixel
                pixels[i] =
                        0xFF000000 |
                                (newR << 16) |
                                (newG << 8) |
                                newB;

                // Quantization error
                int errR = r - newR;
                int errG = g - newG;
                int errB = b - newB;

                // =====================================================
                // Floyd–Steinberg diffusion
                //
                // Right        7/16
                // Bottom-left  3/16
                // Bottom       5/16
                // Bottom-right 1/16
                //
                // Integer math optimization:
                // value * fraction == (value * numerator) >> 4
                // because divide-by-16 == shift-right-4
                // =====================================================

                // RIGHT
                distributeError(pixels, i + 1, errR, errG, errB, 7);

                // BOTTOM-LEFT
                distributeError(pixels, nextRow + x - 1, errR, errG, errB, 3);

                // BOTTOM
                distributeError(pixels, nextRow + x, errR, errG, errB, 5);

                // BOTTOM-RIGHT
                distributeError(pixels, nextRow + x + 1, errR, errG, errB, 1);
            }
        }

        // Push modified pixels back into bitmap
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);

        return bmp;
    }

    private static void distributeError(int[] pixels, int index, int errR, int errG, int errB, int weight) {

        int pixel = pixels[index];

        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;

        // Integer-only diffusion
        r += (errR * weight) >> 4;
        g += (errG * weight) >> 4;
        b += (errB * weight) >> 4;

        // Fast clamp
        if (r < 0) r = 0;
        else if (r > 255) r = 255;

        if (g < 0) g = 0;
        else if (g > 255) g = 255;

        if (b < 0) b = 0;
        else if (b > 255) b = 255;

        pixels[index] =
                0xFF000000 |
                        (r << 16) |
                        (g << 8) |
                        b;
    }


    // ------ Dog-Vision -----------------------------------------------------
    // This is the idea taken from a blog post
    // https://ssodelta.wordpress.com/tag/rgb-to-lms/
    /*
    public class ProtanopiaFilter implements DImageFilter {

        @Override
        public Colour filter(int x, int y, Colour[][] raster) {
            Colour c = raster[x][y];
            c.convert(ColourSpace.LMS);

            double[] lms = c.getData();

            double Lp = 2.02344*lms[1] - 2.52581*lms[2],
                    Mp = lms[1],
                    Sp = lms[2];

            return new Colour(new double[]{Lp, Mp, Sp}, ColourSpace.LMS);
        }

    }
     */

    public static int simulateColorBlindness(int pixel, float[][] matrix) {

        // Extract normalized sRGB
        float r = ((pixel >> 16) & 255) / 255f;
        float g = ((pixel >> 8) & 255) / 255f;
        float b = (pixel & 255) / 255f;

        // Gamma decode
        // This is a piecewise function that basicaly performs gamma correction
        // Which is basically this:
        // if (c small → linear divide)
        // else → exponential curve
        r = srgbToLinear(r);
        g = srgbToLinear(g);
        b = srgbToLinear(b);

        // RGB → LMS
        float[] rgb = { r, g, b };
        float[] lms = multiplyMatrixAndVector(RGB_TO_LMS, rgb);

        // =========================================
        // COLOR BLINDNESS SIMULATION STEP
        // Example: Deuteranopia matrix
        // =========================================

        float[] simLms = multiplyMatrixAndVector(matrix, lms);

        // LMS → RGB
        float[] rgb2 = multiplyMatrixAndVector(LMS_TO_RGB, simLms);

        float outR = rgb2[0];
        float outG = rgb2[1];
        float outB = rgb2[2];

        // Gamma encode
        outR = linearToSrgb(outR);
        outG = linearToSrgb(outG);
        outB = linearToSrgb(outB);

        // Clamp
        outR = Math.max(0f, Math.min(1f, outR));
        outG = Math.max(0f, Math.min(1f, outG));
        outB = Math.max(0f, Math.min(1f, outB));

        // Pack pixel
        int finalR = (int)(outR * 255f);
        int finalG = (int)(outG * 255f);
        int finalB = (int)(outB * 255f);

        return 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
    }

    public static Bitmap toColourBlind(Bitmap src, int kBlind) {
        // TODO fix how the seekbar sets the minimum k value
        // As of now I cant go below 2 in setupFilterSpinner()
        int option = kBlind - 2;

        float[][][] transMatrices = {
                PROTANOPIA,
                DEUTERANOPIA,
                TRITANOPIA,
                DOG_SIMULATION
        };

        float[][] matrix = transMatrices[option];

        Bitmap imageOut = imageToBitmap(src);
        int width = imageOut.getWidth();
        int height = imageOut.getHeight();

        int[] pixels = new int[width * height];

        src.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = simulateColorBlindness(pixels[i], matrix);
        }

        imageOut.setPixels(pixels, 0, width, 0, 0, width, height);
        return imageOut;
    }


    // ------ K-Means --------------------------------------------------------

    public Bitmap rebuildFromClusters(int width, int height, List<float[]> points,
                                      List<float[]> centroids, int[] assignments) {

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int index = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int cluster = assignments[index];
                float[] color = centroids.get(cluster);

                int r = (int) color[0];
                int g = (int) color[1];
                int b = (int) color[2];

                int pixel = Color.rgb(r, g, b);

                result.setPixel(x, y, pixel);

                index++;
            }
        }

        return result;
    }

    // ------ Pixelation -----------------------------------------------------
    public Bitmap pixelateImage(Bitmap src, int PIX_SIZE){

        int height = src.getHeight();
        int width = src.getWidth();

        // How big should the pixelations be?
        //int PIX_SIZE = 10;

        // Get the data (array of pixels)
        // how to get a Bitmap to a array?
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // Loop through every PIX_SIZE pixels, in both x and y directions
        for(int y = 0; y < src.getHeight(); y += PIX_SIZE) {
            for(int x = 0; x < src.getWidth(); x += PIX_SIZE) {

                // Get the pixel
                int pixel = src.getPixel(x, y);

                // "Paste" the pixel onto the surrounding PIX_SIZE by PIX_SIZE neighbors
                // Also make sure that our loop never goes outside the bounds of the image
                for(int yd = y; (yd < y + PIX_SIZE && yd < height); yd++) {
                    for(int xd = x; (xd < x + PIX_SIZE && xd < width); xd++) {
                        result.setPixel(xd, yd, pixel);
                    }
                }
            }
        }
        return result;
    }


    // ------ Interlaced -----------------------------------------------------

    // ---- Interlaced Utils -------------
    private static class OutputImageData {
        int width;
        int height;
        Bitmap bitmap;
        OutputImageData(int width, int height, Bitmap bitmap) {
            this.width = width;
            this.height = height;
            this.bitmap = bitmap;
        }
    }

    private OutputImageData initOutputImage(Bitmap a, Bitmap b) {
        // if the camera is rotated from landscape --> portrait this fails
        int width = Math.min(a.getWidth(), b.getWidth());
        int height = Math.min(a.getHeight(), b.getHeight());
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        return new OutputImageData(width, height, out);
    }

    public Bitmap createInterlacedDistpacter(Bitmap a, Bitmap b, int k) {
        // basic idea for all: mask(x, y, k) → 0 or 1
        // Core algo:
        //      if (mask == 0) pixel from A
        //          else pixel from B
        switch (k % 5) {
            case 0: return interlaceCheckered(a, b, k);
            case 1: return interlaceVerticalStripes(a, b, k);
            case 2: return interlaceHalfHalf(a, b);
            case 3: return interlaceNoise(a, b, k);
            case 4: return interlaceSwirl(a, b, k);
            default: return interlaceCheckered(a, b, k);
        }
    }

    // ----- Interlaced Filters ----------
    public Bitmap createInterlaced(Bitmap a, Bitmap b, int k) {
        //int width = Math.min(a.getWidth(), b.getWidth());
        //int height = Math.min(a.getHeight(), b.getHeight());
        //Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        OutputImageData data = initOutputImage(a, b);
        int width = data.width;
        int height = data.height;
        Bitmap out = data.bitmap;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                //if (y % k == 0) {
                if (y % 2 == 0) {
                    out.setPixel(x, y, a.getPixel(x, y));
                } else {
                    out.setPixel(x, y, b.getPixel(x, y));
                }
            }
        }

        return out;
    }

    private Bitmap interlaceCheckered(Bitmap a, Bitmap b, int k) {

        OutputImageData data = initOutputImage(a, b);
        int width = data.width;
        int height = data.height;
        Bitmap out = data.bitmap;

        int size = Math.max(2, k);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                boolean useA = ((x / size) + (y / size)) % 2 == 0;

                out.setPixel(x, y,
                        useA ? a.getPixel(x, y) : b.getPixel(x, y));
            }
        }
        return out;
    }

    private Bitmap interlaceVerticalStripes(Bitmap a, Bitmap b, int k) {
        OutputImageData data = initOutputImage(a, b);
        int width = data.width;
        int height = data.height;
        Bitmap out = data.bitmap;
        int stripe = Math.max(1, k);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                boolean useA = (x / stripe) % 2 == 0;
                out.setPixel(x, y,
                        useA ? a.getPixel(x, y) : b.getPixel(x, y));
            }
        }
        return out;
    }

    private Bitmap interlaceHalfHalf(Bitmap a, Bitmap b) {
        OutputImageData data = initOutputImage(a, b);
        int width = data.width;
        int height = data.height;
        Bitmap out = data.bitmap;


        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                boolean useA = x < width / 2;
                out.setPixel(x, y,
                        useA ? a.getPixel(x, y) : b.getPixel(x, y));
            }
        }
        return out;
    }

    private Bitmap interlaceNoise(Bitmap a, Bitmap b, int k) {
        OutputImageData data = initOutputImage(a, b);
        int width = data.width;
        int height = data.height;
        Bitmap out = data.bitmap;

        Random rand = new Random(k); // deterministic noise per k

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                boolean useA = rand.nextFloat() > 0.5f;
                out.setPixel(x, y,
                        useA ? a.getPixel(x, y) : b.getPixel(x, y));
            }
        }
        return out;
    }

    private Bitmap interlaceSwirl(Bitmap a, Bitmap b, int k) {
        OutputImageData data = initOutputImage(a, b);
        int width = data.width;
        int height = data.height;
        Bitmap out = data.bitmap;


        float cx = width / 2f;
        float cy = height / 2f;
        float angleScale = k * 0.05f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float dx = x - cx;
                float dy = y - cy;

                double angle = Math.atan2(dy, dx);
                double radius = Math.sqrt(dx * dx + dy * dy);

                // swirl pattern
                double warped = angle + radius * angleScale;

                boolean useA = ((int)(warped * 10)) % 2 == 0;
                out.setPixel(x, y,
                        useA ? a.getPixel(x, y) : b.getPixel(x, y));
            }
        }
        return out;
    }

    // TODO Test the getPattern and each interlaced pattern!!
    private int[][] getPattern(int k) {
        switch (k % 3) {

            // Cross
            case 0:
                return new int[][]{
                        {0,1,0},
                        {1,1,1},
                        {0,1,0}
                };

            // X pattern
            case 1:
                return new int[][]{
                        {1,0,1},
                        {0,1,0},
                        {1,0,1}
                };

            // Square frame
            case 2:
                return new int[][]{
                        {1,1,1},
                        {1,0,1},
                        {1,1,1}
                };

            // Diamond patter
            default:
                return new int[][]{
                        {0,1,0},
                        {1,0,1},
                        {0,1,0}
                };
        }
    }

    private Bitmap interlaceGridPattern(Bitmap a, Bitmap b, int k) {

        OutputImageData data = initOutputImage(a, b);
        int width = data.width;
        int height = data.height;
        Bitmap out = data.bitmap;


        // 3x3 pattern (you can swap this anytime)
        int[][] pattern = getPattern(k);

        int size = 3;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int px = x % size;
                int py = y % size;

                boolean useB = pattern[py][px] == 1;
                out.setPixel(x, y,
                        useB ? b.getPixel(x, y) : a.getPixel(x, y));
            }
        }
        return out;
    }

}
