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



}
