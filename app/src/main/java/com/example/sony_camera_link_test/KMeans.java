package com.example.sony_camera_link_test;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class KMeans {

    // SETTINGS
    private int numClusters;
    private boolean useManhattanDistance;

    // DATA
    private List<float[]> centroids;

    //private List<float[]> points;
    private int[] points;

    // RESULTS
    private int[] assignments;

    // RANDOM
    private Random random;

    // CONSTRUCTOR
    //public KMeans(List<float[]> points, int numClusters) {
    public KMeans(int[] points, int numClusters) {
        this.points = points;
        this.numClusters = numClusters;

        this.centroids = new ArrayList<>();
        this.assignments = new int[points.length];
        //this.assignments = new int[points.size()];
        this.random = new Random();
    }
    // Maybe hardcode numDimensions = 3 b/c RGB?

    // --------------------------------------------
    /*
    private void basicRandSample() {
        //clear old centroids
        //
        //create set of used indices
        //
        //while not enough centroids:
        //    choose random point index
        //
        //    if unused:
        //        add point to centroids
        int numPoints = getNumPoints();

        if (points == null || numPoints == 0 || numClusters <= 0) {
            throw new IllegalStateException("Invalid state for KMeans initialization");
        }

        centroids = new ArrayList<>(numClusters);

        // Track used indices to avoid duplicates
        Set<Integer> usedIndices = new HashSet<>();

        while (centroids.size() < numClusters) {
            int index = random.nextInt(numPoints);

            if (!usedIndices.contains(index)) {
                usedIndices.add(index);

                // Copy the point so centroids don't reference original data
                float[] original = points.get(index);
                float[] centroid = new float[original.length];

                System.arraycopy(original, 0, centroid, 0, original.length);

                centroids.add(centroid);
            }
        }
    }
     */

    // Slower less memory efficient version that uses a list of floats (pixels)
    private void fastRandSample() {
        //int n = points.size();
        int n = points.length;
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            indices.add(i);
        }

        Collections.shuffle(indices, random);

        centroids = new ArrayList<>(numClusters);

        for (int i = 0; i < numClusters; i++) {

            int pixel = points[indices.get(i)];

            float[] centroid = {
                    Color.red(pixel),
                    Color.green(pixel),
                    Color.blue(pixel)
            };

            centroids.add(centroid);
        }
    }

    private void fastRandSampleInitialCentroids() {

        centroids = new ArrayList<>(numClusters);

        for (int i = 0; i < numClusters; i++) {

            int randomIndex = random.nextInt(points.length);
            int pixel = points[randomIndex];

            centroids.add(new float[]{
                    Color.red(pixel),
                    Color.green(pixel),
                    Color.blue(pixel)
            });
        }
    }

    private int findNearestCentroid(float[] points) {
        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;

        int centroidLength = centroids.size();

        for (int i=0; i < centroidLength; i++) {
            float[] centroid = centroids.get(i);

            // float distance = MathUtils.colorEuclideanDistance(points, centroid);
            float distance = MathUtils.fastColorEuclideanDistance(points, centroid);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int findNearestCentroidFastInt(int pixel) {

        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;

        int best = 0;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < centroids.size(); i++) {

            float[] c = centroids.get(i);

            float dr = r - c[0];
            float dg = g - c[1];
            float db = b - c[2];

            float dist = dr * dr + dg * dg + db * db;

            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }

        return best;
    }

    // Slower less memory efficient version that uses a list of floats (pixels)
    private void assignClusters(List<float[]> points){
        for (int i=0; i < points.size(); i++) {
            float[] point = points.get(i);
            int nearestCluster= findNearestCentroid(point);
            assignments[i] = nearestCluster;
        }
    }

    private void assignClustersInt(int[] points) {
        for (int i = 0; i < points.length; i++) {

            int pixel = points[i];

            float[] point = {
                    Color.red(pixel),
                    Color.green(pixel),
                    Color.blue(pixel)
            };

            int nearestCluster = findNearestCentroid(point);
            assignments[i] = nearestCluster;
        }
    }

    private void assignClustersFastInt(int[] points) {
        for (int i = 0; i < points.length; i++) {

            int pixel = points[i];

            int nearestCluster = findNearestCentroidFastInt(pixel);

            assignments[i] = nearestCluster;
        }
    }

    private void updateCentroidsTest(List<float[]> points) {
        int numPoints = getNumPoints();

        //float[][] sums =
    }

    /*
    private void slowUpdateCentroids(List<float[]> points) {

        int k = centroids.size();

        float[][] sums = new float[k][3];
        int[] counts = new int[k];

        // 1. Accumulate sums
        for (int i = 0; i < points.size(); i++) {
            int cluster = assignments[i];
            float[] point = points.get(i);
            MathUtils.addPointToSum(sums[cluster], point);
            counts[cluster]++;
        }

        // 2. Compute means
        for (int i = 0; i < k; i++) {
            if (counts[i] == 0) continue; // don't divide by zero
            float[] centroid = centroids.get(i);
            centroid[0] = sums[i][0];
            centroid[1] = sums[i][1];
            centroid[2] = sums[i][2];
            MathUtils.divideByCount(centroid, counts[i]);
        }
    }
    */

    // Slower less memory efficient version that uses a list of floats (pixels)
    private boolean updateCentroids(List<float[]> points) {

        int k = centroids.size();

        float[][] sums = new float[k][3];
        int[] counts = new int[k];

        // Track if any centroid changed
        boolean changed = false;

        // Small tolerance because floats are noisy
        final float EPSILON = 0.001f;

        // 1. Accumulate sums
        for (int i = 0; i < points.size(); i++) {

            int cluster = assignments[i];
            float[] point = points.get(i);

            MathUtils.addPointToSum(sums[cluster], point);
            counts[cluster]++;
        }

        // 2. Compute means
        for (int i = 0; i < k; i++) {

            if (counts[i] == 0) continue;

            float[] centroid = centroids.get(i);

            // Save old values
            float oldR = centroid[0];
            float oldG = centroid[1];
            float oldB = centroid[2];

            // Update centroid
            centroid[0] = sums[i][0];
            centroid[1] = sums[i][1];
            centroid[2] = sums[i][2];

            MathUtils.divideByCount(centroid, counts[i]);

            // Check if centroid actually moved
            if (Math.abs(oldR - centroid[0]) > EPSILON ||
                    Math.abs(oldG - centroid[1]) > EPSILON ||
                    Math.abs(oldB - centroid[2]) > EPSILON) {

                changed = true;
            }
        }

        return changed;
    }

    private boolean updateCentroidsInt(int[] points) {

        int k = centroids.size();

        float[][] sums = new float[k][3];
        int[] counts = new int[k];

        boolean changed = false;

        final float EPSILON = 0.001f;

        // Accumulate sums
        for (int i = 0; i < points.length; i++) {

            int cluster = assignments[i];
            int pixel = points[i];

            sums[cluster][0] += Color.red(pixel);
            sums[cluster][1] += Color.green(pixel);
            sums[cluster][2] += Color.blue(pixel);

            counts[cluster]++;
        }

        // Compute means
        for (int i = 0; i < k; i++) {

            if (counts[i] == 0) continue;

            float[] centroid = centroids.get(i);

            float oldR = centroid[0];
            float oldG = centroid[1];
            float oldB = centroid[2];

            centroid[0] = sums[i][0] / counts[i];
            centroid[1] = sums[i][1] / counts[i];
            centroid[2] = sums[i][2] / counts[i];

            if (Math.abs(oldR - centroid[0]) > EPSILON ||
                    Math.abs(oldG - centroid[1]) > EPSILON ||
                    Math.abs(oldB - centroid[2]) > EPSILON) {

                changed = true;
            }
        }

        return changed;
    }

    public void run() {

        int maxIterations = 5;

        // 1. initialize centroids
        //basicRandSample();
        //fastRandSample();
        fastRandSampleInitialCentroids();

        for (int i = 0; i < maxIterations; i++) {

            // 2. assign each pixel to nearest centroid
            //assignClusters(points);
            //assignClustersInt(points);
            assignClustersFastInt(points);

            // 3. update centroid positions
            //boolean changed = updateCentroids(points);
            boolean changed = updateCentroidsInt(points);

            // 4. stop early if converged
            if (!changed) {
                break;
            }
        }
    }


    // TODO
    /*
    [x] initializeCentroids()

    [x] findNearestCentroid()

    [x] assignClusters()

    [x] updateCentroids()

    [x] calculateDistance()

    [x] run()
        repeat:
            assignClusters()
            updateCentroids()
        until convergence

    */

    // -------- SETTERS
    /*
    public void setPoints(List<float[]> points) {
        this.points = points;
    }
     */

    public void setPoints(int[] points) {
        this.points = points;
    }

    // -------- GETTERS
    /*
    private int getNumPoints() {
        return points.size();
    }
     */
    private int getNumPoints() {
        return points.length;
    }

    /*
    private int getDimensions() {
        return points.get(0).length;
    }
     */
    private int getDimensions() {
        return points.length;
    }


    public List<float[]> getCentroids() {
        return centroids;
    }

    public int[] getAssignments() {
        return assignments;
    }
}
