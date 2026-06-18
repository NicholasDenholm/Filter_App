package com.example.sony_camera_link_test;

import static com.example.sony_camera_link_test.ImageProcessor.rotateBitmap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContracts;
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
import androidx.core.content.FileProvider;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
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

    // The launcher now lives gracefully inside the client class instance
    private final ActivityResultLauncher<Intent> cameraIntentLauncher;

    private String currentPhotoPath;
    private Uri photoURI;
    private MainActivity.OnBitmapReady fallbackCallback; // Tracks the active UI callback

    Boolean debug = true;

    // Interface callback to notify Activity when data is ready
    public interface AndroidCameraClientListener {
        void onCameraListReady(List<CameraOption> cameras);
    }

    interface OnBitmapReady {
        void onReady(Bitmap bitmap);
    }


    public AndroidCameraClient(Context context, LifecycleOwner lifecycleOwner, ImageView previewView, ActivityResultRegistry registry) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;

        this.cameraIntentLauncher = registry.register("system_camera_fallback", lifecycleOwner,
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        try {
                            Log.d("CAMERA_INTENT", "System camera returned OK. Processing file...");
                            Bitmap fullResBitmap = BitmapFactory.decodeFile(currentPhotoPath);

                            int rotationNeeded = ImageProcessor.getCameraPhotoOrientation(currentPhotoPath);

                            // Fixed rotation issue with front facing camera
                            // with prepopulated cameras: 90 CCW
                            // with system camera front facing 180 CCW/CW
                            Bitmap correctedBitmap = rotateBitmap(fullResBitmap, rotationNeeded);

                            // 3. Fire the stored callback to pass the bitmap safely back to MainActivity
                            if (fallbackCallback != null && correctedBitmap != null) {
                                fallbackCallback.onReady(correctedBitmap);
                            }

                        } catch (Exception e) {
                            Log.e("CAMERA_INTENT", "Failed to parse full resolution photo", e);
                            Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // ── Setup ───────────────────────────────────────────────────────────
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
                    Log.w("CAMERA_CLIENT_DEBUG", "Warning: setupCamera was called, but the passed listener interface is NULL!");
                }

            } catch (Exception e) {
                // If an exception happens anywhere above, it jumps here and bypasses the listener completely!
                Log.e("CAMERA_CLIENT_DEBUG", "CRASH inside setupCamera try/catch block!", e);
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

                // THIS IS THE CORRECTION!
                listener.onCameraListReady(availableCamerasList);

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
            // TODO Try to better populate the menu with this approach
            /*
            for (CameraInfo info : allCameras) {
                String camId = Camera2CameraInfo.from(info).getCameraId();
                @SuppressLint("RestrictedApi") CameraCharacteristics chars = Camera2CameraInfo.extractCameraCharacteristics(info);

                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                float focal = (focalLengths != null && focalLengths.length > 0) ? focalLengths[0] : 0.0f;

                Log.d("POPULATE_CAMERA_MENU_DEBUG", "ID: " + camId + " | Facing: " + facing + " | Focal Length: " + focal);

                // Determine user-friendly label using our resolver
                String label = resolveLensLabel(camId, facing, focal);
                if (label == null) continue;

                // Create item and bundle metadata payload
                MenuItem item = menu.add(Menu.NONE, camId.hashCode(), Menu.NONE, label);
                Intent dataBundle = new Intent();
                dataBundle.putExtra("LOGICAL_ID", camId);
                dataBundle.putExtra("FACING", facing);
                item.setIntent(dataBundle);
            }

            // Inject the explicit fallback system action at the end
            menu.add(Menu.NONE, 999, Menu.NONE, "System Camera");
             */

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

        // Sony camera
        availableCamerasList.add(new CameraOption("Sony Camera", "666", -2));
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

    // TODO use this helper to make menu more informative
    private String resolveLensLabel(String camId, Integer facing, float focal) {
        if (facing == null) return null;

        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return camId.equals("1") ? "Front Camera" : "Front Camera (Wide Angle)";
        }

        if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            if (focal <= 2.5f) {
                return "Ultra-Wide Camera";
            } else if (focal > 6.0f) {
                return "Telephoto Camera";
            } else {
                return "Main Back Camera (ID " + camId + ")";
            }
        }

        return null; // Fallback for unknown or external lenses
    }

    // TODO fix the openSystemCamera app
    /*
    private boolean handleMenuSelection(MenuItem item) {
        // Action A: Escape hatch to native app
        if (item.getItemId() == 999) {
            openSystemCameraApp();
            return true;
        }

        //DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        //drawerLayout.openDrawer(GravityCompat.START);

        // Action B: Bind a custom internal CameraX lens
        Intent intent = item.getIntent();
        if (intent != null) {
            selectedLogicalCameraId = intent.getStringExtra("LOGICAL_ID");
            int facing = intent.getIntExtra("FACING", CameraCharacteristics.LENS_FACING_BACK);

            currentLensFacing = (facing == CameraCharacteristics.LENS_FACING_FRONT)
                    ? CameraSelector.LENS_FACING_FRONT
                    : CameraSelector.LENS_FACING_BACK;

            Toast.makeText(this, item.getTitle() + " Activated", Toast.LENGTH_SHORT).show();
            bindCameraUseCases();
        }
        return true;
    }

     */


    /*
    public void openSystemCameraApp() {
        return;
    }
     */

    /*
    private final ActivityResultLauncher<Intent> cameraIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        // The photo is saved! Decode the file path back into a high-res Bitmap
                        Bitmap fullResBitmap = BitmapFactory.decodeFile(currentPhotoPath);

                        // Fixed 90 CCW rotation issue
                        Bitmap correctedBitmap = rotateBitmap(fullResBitmap, 90);

                        // Pass it directly to your existing method
                        setCurrentImage(correctedBitmap);

                    } catch (Exception e) {
                        Log.e("CAMERA_INTENT", "Failed to parse full resolution photo", e);
                        Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

     */

    public void openSystemCameraApp(MainActivity.OnBitmapReady callback) {
        this.fallbackCallback = callback;
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Cleaned up environment references using 'context.'
        if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
            try {
                File storageDir = context.getCacheDir();
                File imageFile = File.createTempFile(
                        "JPEG_" + System.currentTimeMillis() + "_",
                        ".jpg",
                        storageDir
                );

                currentPhotoPath = imageFile.getAbsolutePath();

                photoURI = FileProvider.getUriForFile(context,
                        context.getApplicationContext().getPackageName() + ".fileprovider",
                        imageFile);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                Log.d("CAMERA_INTENT", "Launching fallback system camera application...");
                cameraIntentLauncher.launch(takePictureIntent);

            } catch (IOException ex) {
                Log.e("CAMERA_INTENT", "Error creating image storage file", ex);
                Toast.makeText(context, "Could not initialize file storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /*
    private void openSystemCameraAppOld() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                // 1. Create an empty temporary file in your app cache
                File storageDir = getCacheDir();
                File imageFile = File.createTempFile(
                         "JPEG_" + System.currentTimeMillis() + "_",
                        ".jpg",
                        storageDir
                        );

                // Save the absolute string path for reading later
                currentPhotoPath = imageFile.getAbsolutePath();

                // Wrap the file in a secure content URI using the provider we defined in Manifest
                photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        imageFile);

                // Tell the Samsung/System camera to drop the full image data into this URI location
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                cameraIntentLauncher.launch(takePictureIntent);

            } catch (IOException ex) {
                Log.e("CAMERA_INTENT", "Error creating image storage file", ex);
                Toast.makeText(this, "Could not initialize file storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

     */

    // ── Selecting Cameras ───────────────────────────────────────────────────────────

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

    public static Camera getCamera() {
        return camera;
    }

    // ── Taking Photos ───────────────────────────────────────────────────────────

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
