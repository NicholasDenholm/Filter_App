package com.example.sony_camera_link_test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class KMeans {

    // SETTINGS
    private int numClusters;
    private boolean useManhattanDistance;

    // DATA
    private List<float[]> centroids;
    private List<float[]> points;

    // RESULTS
    private int[] assignments;

    // RANDOM
    private Random random;

    // CONSTRUCTOR
    public KMeans(List<float[]> points, int numClusters) {
        this.points = points;
        this.numClusters = numClusters;

        this.centroids = new ArrayList<>();
        this.assignments = new int[points.size()];
        this.random = new Random();
    }
    // Maybe hardcode numDimensions = 3 b/c RGB?

    // --------------------------------------------
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
        /*
        List<float[]> centroids = new List<float[]>;
        double[][] copy = points;

        Random gen = new Random();
        int rand;
        for (int i = 0; i < numClusters; i++) {
            // get random number
            rand = gen.nextInt(numPoints - i);

            for (int j = 0; j < n; j++) {
                centroids[i][j] = copy[rand][j];       // store chosen centroid
                copy[rand][j] = copy[m - 1 - i][j];    // ensure sampling without replacement
            }
        }
        */

    }

    private void fastRandSample() {
        int n = points.size();
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            indices.add(i);
        }

        Collections.shuffle(indices, random);

        centroids = new ArrayList<>(numClusters);

        for (int i = 0; i < numClusters; i++) {
            float[] original = points.get(indices.get(i));
            float[] centroid = Arrays.copyOf(original, original.length);
            centroids.add(centroid);
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

    private void assignClusters(List<float[]> points){
        for (int i=0; i < points.size(); i++) {
            float[] point = points.get(i);
            int nearestCluster= findNearestCentroid(point);
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

    public void run() {

        int maxIterations = 10;

        // 1. initialize centroids
        //basicRandSample();
        fastRandSample();

        for (int i = 0; i < maxIterations; i++) {

            // 2. assign each pixel to nearest centroid
            assignClusters(points);

            // 3. update centroid positions
            boolean changed = updateCentroids(points);

            // 4. stop early if converged
            if (!changed) {
                break;
            }
        }
    }
    /*
    public void run() {
        int maxIterations = 20;

        basicRandSample();

        //repeat:

        //assignClusters()
        //updateCentroids()
        //until convergence
    }
    */

    // TODO
    /*
    [x] initializeCentroids()

    [x] findNearestCentroid()

    [x] assignClusters()

    [] updateCentroids()

    [x] calculateDistance()

    [] run()
        repeat:
            assignClusters()
            updateCentroids()
        until convergence

    */

    // -------- SETTERS
    public void setPoints(List<float[]> points) {
        this.points = points;
    }

    // -------- GETTERS
    private int getNumPoints() {
        return points.size();
    }

    private int getDimensions() {
        return points.get(0).length;
    }


    public List<float[]> getCentroids() {
        return centroids;
    }

    public int[] getAssignments() {
        return assignments;
    }
}
