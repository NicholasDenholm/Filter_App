package com.example.sony_camera_link_test;

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

    public static Bitmap toGrayScale(Bitmap src) {
        Bitmap bmp = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(filter);
        canvas.drawBitmap(src, 0, 0, paint);

        return bmp;
    };

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
