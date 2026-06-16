package com.example.sony_camera_link_test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@androidx.annotation.OptIn(markerClass = ExperimentalCamera2Interop.class)
public class AndroidCameraClient {
    private final Context context;
    private static LifecycleOwner lifecycleOwner;
    private static ImageView previewView;

    // Encapsulated State Variables
    private static ProcessCameraProvider cameraProvider;
    private static ImageCapture imageCapture;
    private static Camera camera;
    private final List<CameraOption> availableCamerasList = new ArrayList<>();
    private static CameraOption activeCameraOption;

    Boolean debug = true;

    // Interface callback to notify Activity when data is ready
    public interface AndroidCameraClientListener {
        void onCameraListReady(List<CameraOption> cameras);
    }

    interface OnBitmapReady {
        void onReady(Bitmap bitmap);
    }


    public AndroidCameraClient(Context context, LifecycleOwner lifecycleOwner, ImageView previewView) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
    }

    public void setupCamera(AndroidCameraClientListener listener) {
        Log.d("CAMERA_CLIENT_DEBUG", "1. setupCamera() called inside client. Fetching ProcessCameraProvider instance...");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            Log.d("CAMERA_CLIENT_DEBUG", "2. CameraX background initialization listener has fired.");
            try {
                cameraProvider = cameraProviderFuture.get();
                Log.d("CAMERA_CLIENT_DEBUG", "3. ProcessCameraProvider retrieved successfully.");

                imageCapture = new ImageCapture.Builder().build();

                Log.d("CAMERA_CLIENT_DEBUG", "4. Populating camera hardware list...");
                populateCameraList();

                Log.d("CAMERA_CLIENT_DEBUG", "5. Deciding default camera selection...");
                setDefaultCamera(0);

                // CRITICAL: This is what triggers your MainActivity log!
                if (listener != null) {
                    Log.d("CAMERA_CLIENT_DEBUG", "6. Firing listener.onCameraListReady callback to MainActivity...");
                    listener.onCameraListReady(availableCamerasList);
                } else {
                    Log.w("CAMERA_CLIENT_DEBUG", "⚠️ Warning: setupCamera was called, but the passed listener interface is NULL!");
                }

            } catch (Exception e) {
                // If an exception happens anywhere above, it jumps here and bypasses the listener completely!
                Log.e("CAMERA_CLIENT_DEBUG", "❌ CRASH inside setupCamera try/catch block!", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }
    public void setupCameraBROKEN(AndroidCameraClientListener listener) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                imageCapture = new ImageCapture.Builder().build();

                if (debug) Log.d("SETUP_CAMERA", "Binding camera");
                if (debug) logCameraState("DEFUALT BEFORE");

                // Execute the pipeline in the correct logical order:
                populateCameraList();
                //setupCameraSpinner();
                setDefaultCamera(0);

                if (debug) logCameraState("DEFUALT AFTER");

            } catch (Exception e) {
                Log.e("SETUP_CAMERA", "Failed to setup camera wrappers", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }


    private void populateCameraList() {
        availableCamerasList.clear();
        if (cameraProvider == null) return;

        try {
            for (CameraInfo info : cameraProvider.getAvailableCameraInfos()) {
                int lensFacing = info.getLensFacing();

                // 1. Get the REAL hardware string ID from the system (e.g., "0", "1", "2")
                String realHardwareId = Camera2CameraInfo.from(info).getCameraId();

                // 2. Generate an accurate label based on facing and real ID
                String label;
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    label = "Front Camera (ID: " + realHardwareId + ")";
                } else {
                    // Let the system IDs distinguish main vs ultra-wide
                    label = "Back Camera (ID: " + realHardwareId + ")";
                }

                // 3. Add to list with the genuine system ID
                availableCamerasList.add(new CameraOption(label, realHardwareId, lensFacing));
            }
        } catch (Exception e) {
            Log.e("CAMERA_POPULATE", "Error reading hardware camera IDs", e);
        }

        // System fallback escape hatch
        availableCamerasList.add(new CameraOption("Open System Camera", "999", -1));
    }

    public int setDefaultCamera(int defaultIndex) {
        // return was void back in main activity
        if (availableCamerasList.isEmpty()) {
            Log.e("SETUP DEFAULT CAMERA", "The available camera list is :" + availableCamerasList);
            return -1;
        }

        // Search for a back-facing lens to use as the default configuration
        for (int i = 0; i < availableCamerasList.size(); i++) {
            if (availableCamerasList.get(i).facing == CameraSelector.LENS_FACING_BACK) {
                defaultIndex = i;
                break;
            }
        }
        // Programmatically select the item. This automatically fires onItemSelected listener.
        //switchCameraFacingSpinner.setSelection(defaultIndex);
        return defaultIndex;
    }

    public static void bindCameraUseCasesBySelection(CameraOption cameraOption) {
        if (cameraProvider == null || cameraOption == null) return;

        try {
            cameraProvider.unbindAll();

            // Cant just select the camera by the logical id.
            // You have to make a CameraSelector query then build that..
            // wtf
            CameraSelector cameraSelector = getCameraSelectorByLogicalId(cameraOption.logicalId);
            //selectedLogicalCameraId = cameraOption.logicalId;

            //Preview preview = new Preview.Builder().build();
            //preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Create the new camera
            //camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
            // TODO add preview here when ready
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture);

            Log.d("CAMERA_CLIENT", "CameraX pipeline successfully bound with Preview + ImageCapture.");

        } catch (Exception e) {
            Log.e("CAMERA BIND", "Failed to bind camera", e);
        }
    }

    private static CameraSelector getCameraSelectorByLogicalId(String targetId) {
        return new CameraSelector.Builder()
                .addCameraFilter(cameraInfos -> {
                    List<CameraInfo> filteredList = new ArrayList<>();
                    for (CameraInfo info : cameraInfos) {
                        // Extract the low-level Camera2 logical ID string
                        String hardwareId = Camera2CameraInfo.from(info).getCameraId();

                        if (hardwareId.equals(targetId)) {
                            filteredList.add(info);
                        }
                    }
                    return filteredList;
                })
                .build();
    }


    public void takePhotoAsBitmap(OnBitmapReady callback) {
        // 1. This will now pass because 'imageCapture' is fully alive inside the client!
        if (imageCapture == null) {
            Log.e("CAMERA_CLIENT", "Camera not ready inside client framework.");
            return;
        }

        imageCapture.takePicture(
                Executors.newSingleThreadExecutor(),
                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Log.d("CAMERA_CLIENT", "Hardware capture success. Processing bitmap matrix...");

                        java.nio.ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                        // Extract rotation data before closing the image frame
                        int rotation = image.getImageInfo().getRotationDegrees();
                        image.close(); // Free hardware resource safely

                        Matrix matrix = new Matrix();
                        matrix.postRotate(rotation);
                        Bitmap rotated = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
                        );

                        // 2. REPLACEMENT FOR runOnUiThread: Bounces execution safely back to the UI thread
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (callback != null) {
                                callback.onReady(rotated);
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e("CAMERA_CLIENT", "Hardware failed to take picture", e);
                    }
                }
        );
    }


    // ── Logging ───────────────────────────────────────────────────────────

    public void logCameraState(String checkpointName) {
        StringBuilder state = new StringBuilder();
        state.append("============ CAMERA STATE: ").append(checkpointName).append(" ============\n");

        // Core Clients & Provider
        //state.append("• cameraClient: ").append(cameraClient != null ? "Initialized" : "NULL").append("\n");
        state.append("• cameraProvider: ").append(cameraProvider != null ? "Ready" : "NULL").append("\n");
        state.append("• camera (Active Session): ").append(camera != null ? "Active" : "NULL").append("\n");
        state.append("• imageCapture: ").append(imageCapture != null ? "Ready" : "NULL").append("\n");

        // Legacy / Primitive Trackers
        state.append("• currentLensFacing: ").append(activeCameraOption.facing)
                .append(activeCameraOption.facing == CameraSelector.LENS_FACING_BACK ? " (BACK)" : " (FRONT)").append("\n");
        state.append("• selectedLogicalCameraId: \"").append(activeCameraOption.logicalId).append("\"\n");

        // UI & Lists
        state.append("• availableCamerasList Size: ").append(availableCamerasList.size()).append("\n");
        //state.append("• cameraAdapter: ").append(cameraAdapter != null ? "Instantiated" : "NULL").append("\n");

        // Modern Active Selection Object
        if (activeCameraOption != null) {
            state.append("• activeCameraOption: ")
                    .append(activeCameraOption.label)
                    .append(" | ID: ").append(activeCameraOption.logicalId)
                    .append(" | Facing: ").append(activeCameraOption.facing)
                    .append(" | Fallback: ").append(activeCameraOption.isSystemFallback).append("\n");
        } else {
            state.append("• activeCameraOption: NULL\n");
        }

        state.append("=======================================================");

        Log.d("CAMERA_DEBUG", state.toString());
    }
}
