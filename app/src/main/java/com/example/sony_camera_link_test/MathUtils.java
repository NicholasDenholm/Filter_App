package com.example.sony_camera_link_test;

public class MathUtils {

    public static float colorEuclideanDistance(float[] rgbValues1, float[] rgbValues2) {

        double redterm = rgbValues1[0] - rgbValues2[0];
        double greenterm = rgbValues1[1] - rgbValues2[1];
        double blueterm = rgbValues1[2] - rgbValues2[2];

        double sum = Math.pow(redterm,2) + Math.pow(greenterm,2) + Math.pow(blueterm,2);

        return (float) sum;
    };

    public static float fastColorEuclideanDistance(float[] a, float[] b) {

        float dr = a[0] - b[0];
        float dg = a[1] - b[1];
        float db = a[2] - b[2];

        // squared distance (NO sqrt for K-means efficiency)
        return dr * dr + dg * dg + db * db;
    }

    public static float colorManhattanDistance(float[] a, float[] b) {
        return Math.abs(a[0] - b[0])
                + Math.abs(a[1] - b[1])
                + Math.abs(a[2] - b[2]);
    }

    public static void addPointToSum(float[] sum, float[] point) {
        sum[0] += point[0];
        sum[1] += point[1];
        sum[2] += point[2];
    }

    public static void divideByCount(float[] value, int count) {
        if (count == 0) return;

        value[0] /= count;
        value[1] /= count;
        value[2] /= count;
    }

    // ------- Matricies for colour blindness --------------------------------


    // ---- Luninace
    // Bradford 3x3 matrix to convert XYZ to LMS
    public static final double[][] M_BRADFORD = {
            { 0.8951,  0.2664, -0.1614},
            {-0.7502,  1.7135,  0.0367},
            { 0.0389, -0.0685,  1.0296}
    };

    // Inverse Bradford matrix to convert LMS back to XYZ
    public static final double[][] M_BRADFORD_INV = {
            { 0.986992, -0.147054,  0.159963},
            { 0.432305,  0.518360,  0.049291},
            {-0.008528,  0.040043,  0.968487}
    };

    public static float[] srgbLuminaceCalc(float r, float g, float b) {
        /*
        Luma coefficients: from https://en.wikipedia.org/wiki/Rec._709#Luma_coefficients
            When encoding Y’CBCR video, BT.709 creates gamma-encoded luma (Y’) ...
            using matrix coefficients 0.2126, 0.7152, and 0.0722 ...
            (together they add to 1)
         */
        float luminanceR = 0.2126f * r;
        float luminanceG = 0.7152f * g;
        float luminanceB = 0.0722f * b;

        return new float[] { luminanceR, luminanceG, luminanceB };
    }


    // ---- RGB To LMS
    public static final float[][] RGB_TO_LMS = {
            { 0.4002f, 0.7076f, -0.0808f },
            { -0.2263f, 1.1653f,  0.0457f },
            { 0.0000f, 0.0000f,  0.9182f }
    };

    public static final float[][] LMS_TO_RGB = {
            { 1.8599364f, -1.1293816f, 0.2198974f },
            { 0.3611914f,  0.6388125f, 0.0000064f },
            { 0.0000000f,  0.0000000f, 1.0890636f }
    };

    // ------ Red blind
    public static final float[][] PROTANOPIA = {
            { 0.0f, 1.05118294f, -0.05116099f },
            { 0.0f, 1.0f,        0.0f        },
            { 0.0f, 0.0f,        1.0f        }
    };

    // ---- Green blind
    public static final float[][] DEUTERANOPIA = {
            { 1.0f, 0.0f,        0.0f        },
            { 0.9513092f, 0.0f,  0.04866992f },
            { 0.0f, 0.0f,        1.0f        }
    };

    // ----- Blue blind
    public static final float[][] TRITANOPIA = {
            { 1.0f, 0.0f, 0.0f },
            { 0.0f, 1.0f, 0.0f },
            { -0.86744736f, 1.86727089f, 0.0f }
    };

    public static final float[][] DOG_SIMULATION = {
            { 0.625f, 0.375f, 0.0f },
            { 0.700f, 0.300f, 0.0f },
            { 0.0f,   0.300f, 0.700f }
    };


    public static float[] multiplyMatrixAndVector(float[][] matrix, float[] vector) {
        float[] result = new float[3];
        for (int i = 0; i < 3; i++ ) {
            for (int j = 0; j < 3; j++) {
                result[i] += matrix[i][j] * vector[j];
            }
        }
        return result;

    }



    static float srgbToLinear(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        } else {
            return (float)Math.pow((c + 0.055f) / 1.055f, 2.4);
        }
    }

    static float linearToSrgb(float c) {
        if (c <= 0.0031308f) {
            return 12.92f * c;
        } else {
            return 1.055f * (float)Math.pow(c, 1.0 / 2.4) - 0.055f;
        }
    }




}
