package com.example.sony_camera_link_test;

import android.graphics.Bitmap;

import androidx.lifecycle.ViewModel;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public class CameraViewModel extends AndroidViewModel  {

    public CameraViewModel(@NonNull Application application) {
        super(application);
        loadState();
    }

    //public Bitmap currentImage;
    private String lastImageUri;
    private String selectedFilter = "K-Means";
    private int currentIntensity = 10;
    private FilterConfig currentFilterConfig;

    private boolean downscaleEnabled = true;

    private CameraOption activeCameraOption;

    public void saveState() {
        SharedPreferences prefs = getApplication()
                .getSharedPreferences("camera_state", Context.MODE_PRIVATE);

        prefs.edit()
                .putString("lastImageUri", lastImageUri)
                .putString("selectedFilter", selectedFilter)
                .putInt("currentIntensity", currentIntensity)
                .putBoolean("downscaleEnabled", downscaleEnabled)
                .apply();
    }

    public void loadState() {
        SharedPreferences prefs = getApplication()
                .getSharedPreferences("camera_state", Context.MODE_PRIVATE);

        lastImageUri = prefs.getString("lastImageUri", null);
        selectedFilter = prefs.getString("selectedFilter", "K-Means");
        currentIntensity = prefs.getInt("currentIntensity", 10);
        //currentFilterConfig.setIntensity(prefs.getInt("currentIntensity", 10));
        downscaleEnabled = prefs.getBoolean("downscaleEnabled", true);
    }

    // ---- Images
    public void setLastImageUri(String uri) {
        this.lastImageUri = uri;
        //saveState();
    }
    public String getLastImageUri() {
        return lastImageUri;
    }

    // ---- DownScale
    public void setDownscaleEnabled(boolean enabled) {
        downscaleEnabled = enabled;
        saveState();
    }
    public boolean isDownscaleEnabled() {
        return downscaleEnabled;
    }

    // ---- Filter Strength
    public void setCurrentIntensity(int strength) {
        currentIntensity = strength;
    }
    public int getCurrentIntensity() {
        return currentIntensity;
    }
}