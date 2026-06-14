package com.example.sony_camera_link_test;

import static com.example.sony_camera_link_test.ImageProcessor.processWithDownscale;
import static com.example.sony_camera_link_test.ImageProcessor.rotateBitmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MainActivity extends AppCompatActivity {

    /*
        GUIDE: To add more buttons:
        1. add your button in the ui_[name].xml file
        2. In bind_views add your button by reference id.
        3. add this variable to the below UI references
        4. create if needed a listener in setupButtons()
        5. Find functions that handle any related functionality
        6. edit those functions to change state if needed.
        7. Test your button
     */

    // ── UI references ──────────────────────────────────────────────────────
    private AppColour appColor;

    private ImageView imageView;

    private Spinner filterSpinner;
    private SeekBar seekBarFilterStrength;
    private TextView seekValueLabel;
    private TextView seekFiterStrOptLabel;

    private Button buttonPhoto;
    private Button buttonPhoneCamera;
    private Button buttonProcess;
    private ProgressBar progressBar;
    private SeekBar zoomSeekBar;

    private MaterialButton switchCameraFacingButton;
    private Spinner switchCameraFacingSpinner;
    private MaterialButton downscaleImageButton;

    private DrawerLayout drawerLayout;
    private Button btnOpenLeftMenu;
    private Button btnOpenRightMenu;


    // ── State ──────────────────────────────────────────────────────────────
    // Default on startup
    private String selectedFilter = "K-means";
    private int currentIntensity = 10;
    private FilterConfig currentFilterConfig;

    private Bitmap currentImage;
    private String CurrentImageUrl; // For the Sony camera


    private String currentPhotoPath;
    private Uri photoURI;

    private boolean isCameraCapturing = false;

    private boolean downscaleEnabled = true;
    private boolean usingFrontCamera = true;

    /*
        GUIDE: To add more filters:
    1. add the name of the filter below
    2. set range for seekbar in setupFilterSpinner
    3. write new method called: apply[yourFilterName]
    4. Make new case for the filter in applyFilterOfChoice
    5. Test it out
    */

    // Filter options shown in the spinner
    private static final String[] FILTER_OPTIONS = {
            "K-Means",
            "Pixelate",
            "Grayscale",
            "Interlaced",
            "FloydSteinbergDithering",
            "ColourBlind"
    };

    interface OnBitmapReady {
        void onReady(Bitmap bitmap);
    }

    public interface BitmapFilter {
        // This is used with ImageProcessor.processWithDownscale()
        Bitmap apply(Bitmap bitmap);
    }

    // ── Camera Clients ─────────────────────────────────────────────────────
    private SonyCameraClient cameraClient;
    private ImageCapture imageCapture;

    // Default lens is back
    private int currentLensFacing = CameraSelector.LENS_FACING_BACK;
    private ProcessCameraProvider cameraProvider;

    // Store the exact camera ID we want to use
    private String selectedLogicalCameraId = null;
    private Camera camera;

    private List<String> availableCamerasList = new ArrayList<>();
    private ArrayAdapter<String> cameraAdapter;


    // Then handle the result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        } else {
            Log.e("CAMERA", "Camera permission denied");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shut down CameraX cleanly so it releases the hardware
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider.getInstance(this).get().unbindAll();
            } catch (Exception e) {
                Log.e("CAMERA", "Error releasing camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        //setContentView(R.layout.activity_main);
        //setContentView(R.layout.ui_redesign); // This is the new UI design
        setContentView(R.layout.ui_withdrawers);

        cameraClient = new SonyCameraClient();

        // Checks the version so that the proper save function is called
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
        }

        try {
            //  In OnCreate do the setup
            bindViews();
            setupFilterSpinner();
            currentFilterConfig = new FilterConfig(currentIntensity, null);
            setupSeekBar();
            setupButtons();
            setupSideMenus();

            //setupCamera();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, 100);
            }

        } catch (Exception e) {
            Log.e("SETUP FAILED", "ERROR: " + e);
        }
    }

    // ── Bind all views from the layout ────────────────────────────────────
    private void bindViews() {
        imageView = findViewById(R.id.image_view);

        filterSpinner = findViewById(R.id.filter_spinner);
        seekBarFilterStrength = findViewById(R.id.seekBarFilterStrength);
        seekValueLabel = findViewById(R.id.seek_value_label);
        seekFiterStrOptLabel = findViewById(R.id.label_clusters_block_size);

        buttonPhoto = findViewById(R.id.button_photo); // Sony camera button
        buttonPhoneCamera = findViewById(R.id.button_phone_camera); // Phone button
        buttonProcess = findViewById(R.id.button_process); // Apply filter button
        progressBar = findViewById(R.id.progressBar); // Active when filter is loading
        zoomSeekBar = findViewById(R.id.zoom_seek_bar); // TODO Improve the format/display of this

        switchCameraFacingButton = findViewById(R.id.button_switch_camera);
        switchCameraFacingSpinner = findViewById(R.id.spinner_switch_camera);
        downscaleImageButton = findViewById(R.id.button_scale_image_down);

        drawerLayout = findViewById(R.id.drawer_layout);
        // left and right settings menus
        btnOpenLeftMenu = findViewById(R.id.btn_open_left_menu);
        btnOpenRightMenu = findViewById(R.id.btn_open_right_menu);
    }

    // ── Spinner setup ─────────────────────────────────────────────────────
    private void setupFilterSpinner() {
        // Dark-themed adapter: use simple_spinner_item and override text color in code
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                FILTER_OPTIONS
        ) {
            // Style the closed spinner text
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(0xFFDDDDDD); // light gray
                ((TextView) view).setTextSize(13);
                return view;
            }

            // Style the dropdown list items
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(appColor.WHITE.getColor(this.getContext()));
                ((TextView) view).setBackgroundColor(0xFF1E1E2A);
                ((TextView) view).setPadding(24, 20, 24, 20);
                return view;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(adapter);

        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFilter = FILTER_OPTIONS[position];

                // Set the variant (if any) for each filter type
                // Set the default intensity or option (enum'd)
                // Update the seekbar label to reflect what the value means
                // for each filter type (clusters for k-means, block size for pixelation, etc.)
                switch (position) {
                    // TODO Clean up the options and k comments and code here
                    case 0: // K-means — cluster count 2–22
                        currentFilterConfig.setVariant(null);
                        setSeekBarRange(0, 30);
                        updateSeekBarTicks("2", "16", "30");
                        updateFilterOptionLabel("CLUSTER COUNT");
                        break;
                    case 1: // Pixelation — block size 2–40px
                        currentFilterConfig.setVariant(null);
                        setSeekBarRange(0, 40);
                        updateSeekBarTicks("2", "21", "40");
                        updateFilterOptionLabel("PIXELATION STRENGTH");
                        break;
                    case 2: // Grayscale — 1–2 (no real range needed)
                        //currentFilterConfig.setVariant(null);
                        currentFilterConfig.setVariant(ColourBlindFilterOption.GRAYSCALE);
                        setSeekBarRange(1, 2);
                        updateSeekBarTicks("", "", "");
                        updateFilterOptionLabel("");
                        break;
                    case 3: // Interlace — 0–7
                        currentFilterConfig.setVariant(InterlaceFilterOption.VERTICAL_STRIPES);
                        setSeekBarRange(0, 7);
                        updateSeekBarTicks("0", "", "7");
                        updateFilterOptionLabel("OPTION");
                        break;
                    case 4: // FloydSteinbergDithering — 0–5
                        currentFilterConfig.setVariant(DitherFilterOption.useFloydSteinbergDitheringOption2);
                        setSeekBarRange(0, 6);
                        updateSeekBarTicks("0", "", "6");
                        updateFilterOptionLabel("OPTION");
                        break;
                    case 5: // colour blind — 0–5 options?
                        currentFilterConfig.setVariant(ColourBlindFilterOption.PROTANOPIA);
                        setSeekBarRange(0,5);
                        updateSeekBarTicks("0", "", "5");
                        updateFilterOptionLabel("OPTION");
                        break;
                    default:
                        setSeekBarRange(0, 20);
                        break;
                }

                // Reset to midpoint and update label
                int mid = seekMin + (seekBarFilterStrength.getMax() / 2);
                setSeekBarValue(mid);
                currentFilterConfig.setIntensity(getSeekBarValue());

                //seekValueLabel.setText(String.valueOf(mid));
                seekValueLabel.setText(formatSeekBarLabel(currentFilterConfig));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }


    // ── SeekBar: update live label on every move ──────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private void setupSeekBar() {

        if (seekBarFilterStrength == null) {
            Log.e("SEEK_DEBUG", "CRITICAL ERROR: seekBar variable is NULL! Check your findViewById mapping.");
            return;
        }

        //Log.d("SEEK_DEBUG", "Is seekbar enabled? " + seekBarFilterStrength.isEnabled());

        // ── FIX: Prevent the DrawerLayout from hijacking horizontal drags ──
        seekBarFilterStrength.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            switch (action) {
                case android.view.MotionEvent.ACTION_DOWN:
                    Log.d("SEEK_DEBUG", "Touch ACTION_DOWN detected on seekbar thumb. Requesting parent lock.");
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case android.view.MotionEvent.ACTION_MOVE:
                    Log.d("SEEK_DEBUG", "Touch ACTION_MOVE detected. Current X coordinates: " + event.getX());
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case android.view.MotionEvent.ACTION_UP:
                    Log.d("SEEK_DEBUG", "Touch ACTION_UP detected. Releasing parent lock.");
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;

                case android.view.MotionEvent.ACTION_CANCEL:
                    Log.w("SEEK_DEBUG", "Touch ACTION_CANCEL! A parent layout stole the touch event away.");
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });

        seekBarFilterStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d("SEEK_DEBUG", "onProgressChanged triggered! New progress: " + progress + " | fromUser: " + fromUser);
                try {
                    currentIntensity = getSeekBarValue();
                    Log.d("SEEK_DEBUG", "Calculated currentIntensity: " + currentIntensity);

                    if (currentFilterConfig != null) {
                        currentFilterConfig.setIntensity(currentIntensity);
                    } else {
                        Log.w("SEEK_DEBUG", "Warning: currentFilterConfig is NULL");
                    }

                    updateVariantFromIntensity();

                    if (seekValueLabel != null) {
                        String updatedLabel = formatSeekBarLabel(currentFilterConfig);
                        seekValueLabel.setText(updatedLabel);
                        Log.d("SEEK_DEBUG", "Label updated successfully to: " + updatedLabel);
                    } else {
                        Log.e("SEEK_DEBUG", "Error: seekValueLabel is NULL! Cannot update UI text.");
                    }
                } catch (Exception e) {
                    Log.e("SEEK_DEBUG", "CRITICAL ERROR inside onProgressChanged logic: ", e);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d("SEEK_DEBUG", "User began physically dragging the seekbar.");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d("SEEK_DEBUG", "User stopped physically dragging the seekbar.");
            }
        });
    }

    private String formatSeekBarLabel(FilterConfig config) {
        // Just check at the start if it is kmeans, pixelate, or Grayscale
        if (config.getVariant() == null) {
            return String.valueOf(config.getIntensity());
        }

        if (selectedFilter.equals("Interlaced")) {
            InterlaceFilterOption mode = (InterlaceFilterOption) config.getVariant();
            switch (mode) {
                case CHECKERED:
                    return "Checkered";
                case VERTICAL_STRIPES:
                    return "Vertical Stripes";
                case HALF_HALF:
                    return "Half / Half";
                case NOISE:
                    return "Noise";
                case SWIRL:
                    return "Swirl";
                case GRID_PATTERN:
                    return "Grid";
            }
        }

        if (selectedFilter.equals("FloydSteinbergDithering")) {
            DitherFilterOption mode = (DitherFilterOption) config.getVariant();
            switch (mode) {
                case useFloydSteinbergDitheringOption1:
                    return "Floyd-Steinberg A";
                case useFloydSteinbergDitheringOption2:
                    return "Floyd-Steinberg B";
                case glitchyDither:
                    return "Glitchy Dither";
                case deepFried:
                    return "Deep Fried";
                case spookyDither:
                    return "Spooky Dark Dither";
            }
        }

        if (selectedFilter.equals("ColourBlind")) {
            ColourBlindFilterOption mode = (ColourBlindFilterOption) config.getVariant();
            switch (mode) {
                case PROTANOPIA:
                    return "Protanopia";
                case DEUTERANOPIA:
                    return "Deuteranopia";
                case TRITANOPIA:
                    return "Tritanopia";
                case DOG_SIMULATION:
                    return "Dog Vision";
            }
        }

        if (selectedFilter.equals("Grayscale")) {
            return "Grayscale";
        }

        // Default fallback
        Log.v("SEEK BAR FORMATING", "formatSeekBarLabel default value set");
        return String.valueOf(config.getIntensity());
    }

    private void updateVariantFromIntensity() {
        Enum<?> variant = currentFilterConfig.getVariant();

        if (variant instanceof InterlaceFilterOption) {
            InterlaceFilterOption[] values = InterlaceFilterOption.values();
            int index = Math.min(currentFilterConfig.getIntensity() - 2, values.length - 1);
            currentFilterConfig.setVariant(values[index]);
        }
        else if (variant instanceof DitherFilterOption) {
            DitherFilterOption[] values = DitherFilterOption.values();
            Log.v("VALUES", "Values are: " + Arrays.toString(values));
            Log.v("SEEK BAR", "option BEFORE is " + currentFilterConfig.getVariant());
            Log.v("SEEK BAR", "intensity BEFORE is " + currentFilterConfig.getIntensity());
            int index = Math.min(currentFilterConfig.getIntensity() - 2, values.length - 1);
            currentFilterConfig.setVariant(values[index]);
            Log.v("SEEK BAR", "option AFTER is " + currentFilterConfig.getVariant());
            Log.v("SEEK BAR", "intensity AFTER is " + currentFilterConfig.getIntensity());
        }
        else if (variant instanceof ColourBlindFilterOption) {
            ColourBlindFilterOption[] values = ColourBlindFilterOption.values();
            Log.v("VALUES", "Values are: " + Arrays.toString(values));
            Log.v("SEEK BAR", "option BEFORE is " + currentFilterConfig.getVariant());
            Log.v("SEEK BAR", "intensity BEFORE is " + currentFilterConfig.getIntensity());
            int index = Math.min(currentFilterConfig.getIntensity() - 2, values.length - 1);
            currentFilterConfig.setVariant(values[index]);
            Log.v("SEEK BAR", "option AFTER is " + currentFilterConfig.getVariant());
            Log.v("SEEK BAR", "intensity AFTER is " + currentFilterConfig.getIntensity());
        }
    }

    // ── Buttons ───────────────────────────────────────────────────────────
    private void setupButtons() {

        // Sony Camera button: launch camera
        buttonPhoto.setOnClickListener(v -> {
            takePhotoAsBitmapSony(bitmap -> {
                currentImage = bitmap;
                ImageView imageView = findViewById(R.id.image_view);
                imageView.setImageBitmap(bitmap);
            });
        });

        // Default phone Camera button (backfacing)
        buttonPhoneCamera.setOnClickListener(v -> {
            takePhotoAsBitmap(bitmap -> {
                currentImage = bitmap;
                imageView.setImageBitmap(bitmap);
            });
        });

        // Apply filter button
        buttonProcess.setOnClickListener(v -> {
            applyFilter(selectedFilter, currentIntensity);
        });

        // Camera type menu
        Log.d("SETUP BUTTONS", "Binding camera, currentLensFacing is " + currentLensFacing);
        switchCameraFacingButton.setOnClickListener(v -> {
            //switchCamera();
            //showCameraMenu();
            //openSystemCameraApp();
            setupCameraMenu();
        });
        // Set colour for the Camera type button
        switchCameraFacingButton.setBackgroundTintList(
                ColorStateList.valueOf(appColor.MEDIUM_PURPLE.getColor(this)));

        downscaleImageButton.setOnClickListener(v -> changeDownScaleOption() );

        // Set colour for downscale image toggle
        downscaleImageButton.setBackgroundTintList(
                ColorStateList.valueOf(appColor.WHITE.getColor(this)));

        // Set up zoom seek bar
        setupZoomSeekBar();
    }

    private void setupSideMenus() {
        Boolean debug = false;
        if (debug) {
            Log.d("DRAWER_DEBUG", "Initialization - Drawer null? " + (drawerLayout == null));
            Log.d("DRAWER_DEBUG", "Initialization - Left Button null? " + (btnOpenLeftMenu == null));
            Log.d("DRAWER_DEBUG", "Initialization - Right Button null? " + (btnOpenRightMenu == null));
        }

        if (btnOpenLeftMenu != null && drawerLayout != null) {
            btnOpenLeftMenu.setOnClickListener(v -> {
                if (debug) Log.d("DRAWER_DEBUG", "Left menu button physically tapped!");
                try {
                    if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                        if (debug) Log.d("DRAWER_DEBUG", "Left drawer was open. Closing it now.");
                        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                    } else {
                        if (debug) Log.d("DRAWER_DEBUG", "Left drawer was closed. Opening it now.");
                        drawerLayout.openDrawer(androidx.core.view.GravityCompat.START);
                    }
                } catch (Exception e) {
                    Log.e("DRAWER_DEBUG", "Crash trying to toggle Left Drawer: ", e);
                }
            });
        }

        // Right Button Click Listener
        if (btnOpenRightMenu != null && drawerLayout != null) {
            btnOpenRightMenu.setOnClickListener(v -> {
                if (debug) Log.d("DRAWER_DEBUG", "Right menu button physically tapped!");
                try {
                    if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.END)) {
                        if (debug) Log.d("DRAWER_DEBUG", "Right drawer was open. Closing it now.");
                        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END);
                    } else {
                        if (debug) Log.d("DRAWER_DEBUG", "Right drawer was closed. Opening it now.");
                        drawerLayout.openDrawer(androidx.core.view.GravityCompat.END);
                    }
                } catch (Exception e) {
                    Log.e("DRAWER_DEBUG", "Crash trying to toggle Right Drawer: ", e);
                }
            });
        }

    }

    // ── Cameras ───────────────────────────────────────────────────────────
    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                imageCapture = new ImageCapture.Builder().build();
                Log.d("CAMERA", "Binding camera");
                bindCameraUseCases();

            } catch (Exception e) {
                Log.e("CAMERA SETUP", "Failed to setup camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        CameraSelector.Builder selectorBuilder = new CameraSelector.Builder();

        // Filter hardware strictly by the exact String ID chosen from your menu
        selectorBuilder.addCameraFilter(cameraInfos -> {
            List<CameraInfo> filteredList = new ArrayList<>();
            for (CameraInfo info : cameraInfos) {
                String hardwareId = Camera2CameraInfo.from(info).getCameraId();
                if (hardwareId.equals(selectedLogicalCameraId)) {
                    filteredList.add(info);
                }
            }
            return filteredList;
        });

        CameraSelector cameraSelector = selectorBuilder.build();

        try {
            cameraProvider.unbindAll();

            // Bind your use cases!
            // (Note: If you have a PreviewView layout, make sure to add 'preview' here too!)
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);

            // Snap our zoom slider back to zero since a new hardware lens just activated
            if (zoomSeekBar != null) {
                zoomSeekBar.setProgress(0);
            }

        } catch (Exception e) {
            Log.e("CAMERA BIND", "Failed to bind camera to ID: " + selectedLogicalCameraId, e);
        }
    }

    private void bindCameraUseCasesOld() {
        if (cameraProvider == null) return;

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(currentLensFacing).build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
        } catch (Exception e) {
            Log.e("CAMERA BIND", "Failed to bind camera", e);
        }

    }

    // Old method to do a simple switch from back <--> front cameras
    private void switchCamera() {

        Log.d("SWITCH CAMERA", "currentLensFacing is " + currentLensFacing);
        if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            currentLensFacing = CameraSelector.LENS_FACING_FRONT;

            switchCameraFacingButton.setBackgroundTintList(
                    ColorStateList.valueOf(appColor.DARK_PURPLE.getColor(this)));

            //switchCameraFacingButton.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));
            Toast.makeText(this, "Front Camera", Toast.LENGTH_SHORT).show();
        } else {
            currentLensFacing = CameraSelector.LENS_FACING_BACK;

            switchCameraFacingButton.setBackgroundTintList(
                    ColorStateList.valueOf(appColor.MEDIUM_PURPLE.getColor(this)));
            Toast.makeText(this, "Back Camera", Toast.LENGTH_SHORT).show();
        }

        bindCameraUseCases();
    }

    //TODO test this
    private void setupCameraSpinner() {
        // Initialize adapter with your dynamic arraylist
        cameraAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, availableCamerasList);
        cameraAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        switchCameraFacingSpinner.setAdapter(cameraAdapter);

        // Listen for users changing selection inside the spinner dropdown
        switchCameraFacingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCamera = availableCamerasList.get(position);

                // Pass the string to your existing handler logic!
                // ex: handleCameraSelection(selectedCamera);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Optional default handler
            }
        });
    }
    /*
    private void setupCameraMenuList() {
        if (cameraProvider == null) return;

        // 1. Clear out any stale entries from a previous run
        availableCamerasList.clear();

        // 2. Query your actual system info (Mimicking your old populateCameraMenu logic)
        // Replace this loop logic with whatever your old populate method used to find cameras!
        for (CameraInfo cameraInfo : cameraProvider.getAvailableCameraInfos()) {
            String cameraDisplayName = convertCameraInfoToName(cameraInfo); // standardizing string labels
            availableCamerasList.add(cameraDisplayName);
        }

        // Fallback safeguard to keep layout from breaking if zero hardware is attached
        if (availableCamerasList.isEmpty()) {
            availableCamerasList.add("No Cameras Found");
        }

        // 3. CRITICAL: Tell the Spinner data adapter to refresh the interface right away
        cameraAdapter.notifyDataSetChanged();
    }

     */

    private void setupCameraMenu() {
        if (cameraProvider == null) return;

        PopupMenu popupMenu = new PopupMenu(this, switchCameraFacingButton);

        // Fill the menu with items
        populateCameraMenu(popupMenu.getMenu());

        // Route the click logic to a dedicated handler
        popupMenu.setOnMenuItemClickListener(this::handleMenuSelection);

        popupMenu.show();
    }

    @OptIn(markerClass = {ExperimentalCamera2Interop.class, ExperimentalCamera2Interop.class})
    private void populateCameraMenu(Menu menu) {
        try {
            List<CameraInfo> allCameras = cameraProvider.getAvailableCameraInfos();

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

        } catch (Exception e) {
            Log.e("CAMERA MENU", "Error populating camera list", e);
        }
    }

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

    @OptIn(markerClass = {ExperimentalCamera2Interop.class, ExperimentalCamera2Interop.class})
    private void showCameraMenu() {
        if (cameraProvider == null) return;

        PopupMenu popupMenu = new PopupMenu(this, switchCameraFacingButton);
        Menu menu = popupMenu.getMenu();

        try {
            // 1. Grab every single camera the system exposes
            List<CameraInfo> allCameras = cameraProvider.getAvailableCameraInfos();

            for (CameraInfo info : allCameras) {
                String camId = Camera2CameraInfo.from(info).getCameraId();
                @SuppressLint("RestrictedApi") CameraCharacteristics chars = Camera2CameraInfo.extractCameraCharacteristics(info);

                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                float focal = (focalLengths != null && focalLengths.length > 0) ? focalLengths[0] : 0.0f;

                // DIAGNOSTIC LOG - Look at this to see your phone's exact blueprint
                Log.d("SAMSUNG_DIAGNOSTIC", "ID: " + camId + " | Facing: " + facing + " | Focal Length: " + focal);

                String label;

                // 2. Remove the "camId.equals("0")" restriction so we judge strictly by focal length
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    label = "Front Camera (ID " + camId + ")";
                } else if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (focal <= 2.5f) {
                        label = "Ultra-Wide Camera";
                    } else if (focal > 6.0f) {
                        label = "Telephoto Camera";
                    } else {
                        label = "Main Back Camera (ID " + camId + ")";
                    }
                } else {
                    continue;
                }

                MenuItem item = menu.add(Menu.NONE, camId.hashCode(), Menu.NONE, label);

                Intent dataBundle = new Intent();
                dataBundle.putExtra("LOGICAL_ID", camId);
                dataBundle.putExtra("FACING", facing);
                item.setIntent(dataBundle);
            }

            // Giving this camera a distinct ID (999) so it won't conflict with the dynamic camera hashCodes
            menu.add(Menu.NONE, 999, Menu.NONE, "System Camera (Use Telephoto)");
            /*
            for (CameraInfo info : allCameras) {
                String camId = Camera2CameraInfo.from(info).getCameraId();
                @SuppressLint("RestrictedApi") CameraCharacteristics chars = Camera2CameraInfo.extractCameraCharacteristics(info);

                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

                if (focalLengths != null) {
                    // smaller focal lengths mean a wider field of view --> wide angle
                    // larger focal lengths mean a narrower, magnified field of view --> telephoto
                    Log.d("CAMERA MENU", "focal lengths are " + focalLengths[0]);
                }

                String label;

                // 2. Identify the lens type cleanly in one flat structure
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    label = "Front Camera";
                } else if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (camId.equals("0")) {
                        label = "Main Back Camera (Auto)";
                    } else if (focalLengths != null && focalLengths.length > 0) {
                        float focal = focalLengths[0];

                        if (focal <= 2.5f) {
                            label = "Ultra-Wide Camera";
                        } else if (focal > 6.0f) {
                            label = "Telephoto Camera";
                        } else {
                            label = "Secondary Standard Camera (" + camId + ")";
                        }
                    } else {
                        label = "Extra Lens (" + camId + ")";
                    }

                    if (camId.equals("0")) {
                        label = "Main Back Camera (Auto)";
                    } else if (focalLengths != null && focalLengths.length > 0 && focalLengths[0] < 3.5f) {
                        label = "Ultra-Wide Camera";
                    } else if (focalLengths != null && focalLengths.length > 0 && focalLengths[0] > 6.0f) {
                        label = "Telephoto Camera";
                    } else {
                        label = "Extra Lens (" + camId + ")";
                    }


                } else {
                    continue; // Skip external or unknown lenses safely
                }

                // 3. Build the menu item and pass data cleanly via an Intent package
                MenuItem item = menu.add(Menu.NONE, camId.hashCode(), Menu.NONE, label);

                Intent dataBundle = new Intent();
                dataBundle.putExtra("LOGICAL_ID", camId);
                dataBundle.putExtra("FACING", facing);
                item.setIntent(dataBundle);
            }

             */

        } catch (Exception e) {
            Log.e("CAMERA MENU", "Error populating camera list", e);
        }

        // 4. One unified click listener to handle any lens selected
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 999) {
                // Option A: User wants access to all cameras (needed for Samsung phones <2022 )
                openSystemCameraApp();
            } else {
                // Option B: User chose an embedded custom CameraX lens
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
            }
            return true;
        });
        popupMenu.show();
    }

    /*
    private void showCameraMenu() {
        if (cameraProvider == null) return;

        PopupMenu popupMenu = new PopupMenu(this, switchCameraFacingButton);
        Menu menu = popupMenu.getMenu();

        try {
            // Add the Defaults
            menu.add(Menu.NONE, 1, Menu.NONE, "Default Back Camera");
            menu.add(Menu.NONE, 0, Menu.NONE, "Default Front Camera");

            // Iterate through ALL logical cameras
            List<CameraInfo> allCameras = cameraProvider.getAvailableCameraInfos();
            for (CameraInfo info : allCameras) {
                String camId = Camera2CameraInfo.from(info).getCameraId();
                @SuppressLint("RestrictedApi") CameraCharacteristics chars = Camera2CameraInfo.extractCameraCharacteristics(info);

                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

                // We only care about extra back cameras right now, skip standard "0" as it's the default
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK && !camId.equals("0")) {

                    String lensName = "Back Lens (" + camId + ")";

                    // Guess the lens type based on focal length
                    if (focalLengths != null && focalLengths.length > 0) {
                        float focalLength = focalLengths[0];
                        if (focalLength < 3.5f) {
                            lensName = "Back Ultra-Wide (" + camId + ")";
                        } else if (focalLength > 6.0f) {
                            lensName = "Back Telephoto (" + camId + ")";
                        }
                    }

                    MenuItem item = menu.add(Menu.NONE, camId.hashCode(), Menu.NONE, lensName);
                    item.getIntent().putExtra("LOGICAL_ID", camId); // Save the exact ID
                }
            }
        } catch (Exception e) {
            Log.e("CAMERA MENU", "Failed to build menu", e);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int selectedMenuId = item.getItemId();

            if (selectedMenuId == 1) {
                currentLensFacing = CameraSelector.LENS_FACING_BACK;
                selectedLogicalCameraId = null; // Let CameraX choose
                Toast.makeText(this, "Default Back Camera", Toast.LENGTH_SHORT).show();

            } else if (selectedMenuId == 0) {
                currentLensFacing = CameraSelector.LENS_FACING_FRONT;
                selectedLogicalCameraId = null; // Let CameraX choose
                Toast.makeText(this, "Default Front Camera", Toast.LENGTH_SHORT).show();

            } else {
                // A specific logical lens was chosen!
                currentLensFacing = CameraSelector.LENS_FACING_BACK;
                selectedLogicalCameraId = item.getIntent().getStringExtra("LOGICAL_ID");
                Toast.makeText(this, item.getTitle(), Toast.LENGTH_SHORT).show();
            }

            bindCameraUseCases();
            return true;
        });

        popupMenu.show();
    }

    private void showCameraMenu() {
        if (cameraProvider == null) {
            Toast.makeText(this, "Camera not initialized yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Attach the popup menu to your existing switch button
        PopupMenu popupMenu = new PopupMenu(this, switchCameraFacingButton);
        Menu menu = popupMenu.getMenu();

        try {

            // Dynamically check if the device has a back camera
            //if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                //menu.add(Menu.NONE, CameraSelector.LENS_FACING_BACK, Menu.NONE, "Back Camera");
            //}

            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                // We use 100 as a custom ID for the auto-switching logical camera
                menu.add(Menu.NONE, 1, Menu.NONE, "Back Camera (Auto-Switch)");

                // Find specific physical back cameras (Ultra-wide, Telephoto, etc.)
                CameraInfo logicalBackInfo = cameraProvider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA);
                Set<CameraInfo> physicalCameras = logicalBackInfo.getPhysicalCameraInfos();
                Log.d("CAMERA MENU", "physicalCameras are " + physicalCameras); // 2026-06-08 22:07:22.356 23520-23520 CAMERA MENU             com.example.sony_camera_link_test    D  physicalCameras are []

                if (physicalCameras.size() > 1) {
                    for (CameraInfo physicalInfo : physicalCameras) {

                        // Use Camera2Interop to get hardware characteristics
                        String physicalId = Camera2CameraInfo.from(physicalInfo).getCameraId();
                        @SuppressLint("RestrictedApi") CameraCharacteristics chars = Camera2CameraInfo.extractCameraCharacteristics(physicalInfo);
                        float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

                        String lensName = "Back Lens (" + physicalId + ")";

                        // Guess the lens type based on focal length (rough estimates)
                        if (focalLengths != null && focalLengths.length > 0) {
                            float focalLength = focalLengths[0];
                            if (focalLength < 3.0f) {
                                lensName = "Back Ultra-Wide";
                            } else if (focalLength > 6.0f) {
                                lensName = "Back Telephoto";
                            } else {
                                lensName = "Back Main (Wide)";
                            }
                        }

                        // We use the string hashcode as a menu ID so we can retrieve it later
                        MenuItem item = menu.add(Menu.NONE, physicalId.hashCode(), Menu.NONE, lensName);

                        // We attach the raw String ID to the Intent of the MenuItem for easy retrieval
                        item.getIntent().putExtra("PHYSICAL_ID", physicalId);
                    }
                } else {
                    // FALLBACK: The device doesn't group lenses. Let's see if it exposes them as separate logical cameras instead.
                    Log.d("CAMERA MENU", "No physical sub-cameras found. Checking all logical cameras.");

                    List<CameraInfo> allCameras = cameraProvider.getAvailableCameraInfos();
                    Log.d("CAMERA MENU", "allCameras are " + allCameras);
                    for (CameraInfo info : allCameras) {
                        String camId = Camera2CameraInfo.from(info).getCameraId();

                        // Only add it if it's NOT the default "0" back camera we already added above
                        if (!camId.equals("0")) {
                            try {
                                @SuppressLint("RestrictedApi") CameraCharacteristics chars = Camera2CameraInfo.extractCameraCharacteristics(info);
                                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                                // If it's another back-facing camera
                                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                                    menu.add(Menu.NONE, camId.hashCode(), Menu.NONE, "Extra Back Lens (" + camId + ")")
                                            .getIntent().putExtra("PHYSICAL_ID", camId);
                                }
                            } catch (Exception e) {
                                Log.e("CAMERA MENU", "Could not extract characteristics for camera " + camId);
                            }
                        }
                    }
                }
            }

            // Dynamically check if the device has a front camera
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                menu.add(Menu.NONE, CameraSelector.LENS_FACING_FRONT, Menu.NONE, "Front Camera");
            }

            // (Optional) Check for external cameras like USB webcams on tablets
            // if (cameraProvider.hasCamera(new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL).build())) {
            //     menu.add(Menu.NONE, CameraSelector.LENS_FACING_EXTERNAL, Menu.NONE, "External Camera");
            // }

        } catch (CameraInfoUnavailableException e) {
            Log.e("CAMERA BIND", "Failed to get available cameras", e);
        }

        // Handle what happens when the user selects a camera from the list
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int selectedLens = item.getItemId();

                // Only rebind if they picked a different camera
                if (currentLensFacing != selectedLens) {
                    currentLensFacing = selectedLens;

                    // Update UI based on selection (using your existing color logic)
                    if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
                        switchCameraFacingButton.setBackgroundTintList(
                                ColorStateList.valueOf(appColor.DARK_PURPLE.getColor(MainActivity.this)));
                        Toast.makeText(MainActivity.this, "Front Camera Selected", Toast.LENGTH_SHORT).show();
                    } else {
                        switchCameraFacingButton.setBackgroundTintList(
                                ColorStateList.valueOf(appColor.MEDIUM_PURPLE.getColor(MainActivity.this)));
                        Toast.makeText(MainActivity.this, "Back Camera Selected", Toast.LENGTH_SHORT).show();
                    }

                    // Rebind the camera with the newly selected lens
                    bindCameraUseCases();
                }
                return true;
            }
        });

        popupMenu.show();
    }
    */

    /*
    private ActivityResultLauncher<Intent> cameraIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // The user successfully took a photo!
                    Toast.makeText(this, "Photo captured from Samsung App!", Toast.LENGTH_SHORT).show();

                    // TODO: Load the image file into your ImageView or handle the data
                    // If you didn't pass a file URI, a low-res thumbnail is available via:
                    // Bitmap thumbnail = (Bitmap) result.getData().getExtras().get("data");
                }
            }
    );

    private void openSystemCameraApp() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure there is a camera app available to handle the request
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraIntentLauncher.launch(takePictureIntent);
        } else {
            Toast.makeText(this, "No camera app found on this device", Toast.LENGTH_SHORT).show();
        }
    }

     */

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

    private void openSystemCameraApp() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                // 1. Create an empty temporary file in your app cache
                File storageDir = getCacheDir();
                File imageFile = File.createTempFile(
                        "JPEG_" + System.currentTimeMillis() + "_", /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
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


    private void setupZoomSeekBar() {
        if (zoomSeekBar == null) return;

        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Only update zoom if the user dragged it and a CameraX lens is active
                if (fromUser && camera != null) {
                    float linearZoomPercentage = progress / 100f;
                    camera.getCameraControl().setLinearZoom(linearZoomPercentage);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ---------- Changing UI values -----------------------------------------
    private void changeDownScaleOption() {
        downscaleEnabled = !downscaleEnabled;
        downscaleImageButton.setChecked(downscaleEnabled);

        if (downscaleEnabled) {
            downscaleImageButton.setBackgroundTintList(
                    ColorStateList.valueOf(appColor.WHITE.getColor(this)));
            Toast.makeText(this, "Downscale Enabled", Toast.LENGTH_SHORT).show();
        } else {
            downscaleImageButton.setBackgroundTintList(
                    ColorStateList.valueOf(appColor.TEXT_DARK_GREY.getColor(this)));
            Toast.makeText(this, "Downscale Disabled", Toast.LENGTH_SHORT).show();
        }
        Log.d("SETUP BUTTONS", "downscale = " + downscaleEnabled);
    }


    // Track the minimum offset manually instead of using SeekBar.setMin() (API 26+).
    // The SeekBar internally always runs from 0; add seekMin when reading progress.
    private int seekMin = 0;

    // Helper: set the seekbar range without using setMin/getMin
    private void setSeekBarRange(int min, int max) {
        /*
        // min number of clusters for Kmeans has to be <0 so this is a workaround
        min += 2;
        max += 2;

        int mid = min + ((max - min) / 2);

        // Convert ints to Strings
        String sMin = String.valueOf(min);
        String sMid = String.valueOf(mid);
        String sMax = String.valueOf(max);

        updateSeekBarTicks(sMin, sMid, sMax);

         */

        seekMin = min;
        seekBarFilterStrength.setMax(max - min);      // internal max is always (real max − real min)
    }

    // Helper: get the real value (adds the offset back)
    private int getSeekBarValue() {
        return seekBarFilterStrength.getProgress() + seekMin;
    }

    // Helper: set the real value (subtracts the offset)
    private void setSeekBarValue(int value) {
        seekBarFilterStrength.setProgress(value - seekMin);
    }

    private void updateSeekBarTicks(String start, String mid, String end) {
        TextView tickStart = findViewById(R.id.seek_bar_tick_start);
        TextView tickMid = findViewById(R.id.seek_bar_tick_mid);
        TextView tickEnd = findViewById(R.id.seek_bar_tick_end);

        // Guard against null pointers if the view isn't loaded yet
        if (tickStart != null) tickStart.setText(start);
        if (tickMid != null) tickMid.setText(mid);
        if (tickEnd != null) tickEnd.setText(end);
    }

    private void updateFilterOptionLabel(String newText) {
        seekFiterStrOptLabel = findViewById(R.id.label_clusters_block_size);
        if (seekFiterStrOptLabel != null) {
            seekFiterStrOptLabel.setText(newText);
        }
    }

    // ── Filter application ────────────────────────────────────────────────
    private void applyFilter(String filter, int intensity) {
        // Show spinner, disable button while processing
        //progressBar.setVisibility(View.VISIBLE);
        //buttonProcess.setEnabled(false);

        // Trying just old method instead of async
        setLoading(true);
        //applyFilterOfChoice(filter, intensity);
        applyConfigFilterOfChoice(filter, currentFilterConfig);

    }

    private void takePhotoAsBitmap(OnBitmapReady callback) {
        if (imageCapture == null) {
            Log.e("CAMERA", "Camera not ready");
            return;
        }

        isCameraCapturing = true;

        imageCapture.takePicture(
                Executors.newSingleThreadExecutor(),
                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    public void onCaptureSuccess(ImageProxy image) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        image.close();

                        int rotation = image.getImageInfo().getRotationDegrees();
                        Matrix matrix = new Matrix();
                        matrix.postRotate(rotation);
                        Bitmap rotated = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
                        );

                        isCameraCapturing = false;
                        // ← wrap callback in runOnUiThread so callers can safely touch views
                        runOnUiThread(() -> callback.onReady(rotated));
                    }

                    @Override
                    public void onError(ImageCaptureException e) {
                        Log.e("CAMERA", "Failed to take picture", e);
                    }
                }
        );
    }

    private void takePhotoAsBitmapSony(OnBitmapReady callback) {
        cameraClient.takePicture(new SonyCameraClient.OnPictureTakenListener() {

            @Override
            public void onSuccess(String imageUrl) {
                Glide.with(MainActivity.this)
                        .asBitmap()
                        .load(imageUrl)
                        .into(new CustomTarget<Bitmap>() {

                            @Override
                            public void onResourceReady(Bitmap resource,
                                                        Transition<? super Bitmap> transition) {
                                callback.onReady(resource);
                            }

                            @Override
                            public void onLoadCleared(Drawable placeholder) {
                                // TODO optional
                            }
                        });
            }

            @Override
            public void onError(Exception e) {
                Log.e("SONY CAMERA", "Failed to take picture", e);
            }
        });
    }

    // ------------------- Setters -------------------
    public void setCurrentImage(Bitmap currentImage) {
        this.currentImage = currentImage;
        ImageView imageView = findViewById(R.id.image_view);
        imageView.setImageBitmap(currentImage);
    }

    //TODO This may of been replaced. Verify and remove if not needed.
    // It is used in the applyfilter method? Is this needed?
    private void setLoading(boolean loading) {
        // Apply changes to already created button!
        //Button processButton = findViewById(R.id.button_process);
        //ProgressBar progressBar = findViewById(R.id.progressBar);

        if (loading) {
            buttonProcess.setEnabled(false);
            buttonProcess.setText("Processing...");
            progressBar.setVisibility(View.VISIBLE);

        } else {
            buttonProcess.setEnabled(true);
            buttonProcess.setText("Apply Filter");
            progressBar.setVisibility(View.GONE);
        }
    }

    // ── Async work ──────────────────────────────────────────────
    // background: the heavy work — runs on worker thread, returns a Bitmap
    // onDone:     UI update — runs on main thread after background finishes
    private void runAsync(Supplier<Bitmap> background, Consumer<Bitmap> onDone) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Bitmap result = background.get();
            runOnUiThread(() -> onDone.accept(result));
        });
    }

    public interface OnFilterDoneCallback {
        void onDone();
    }

    // ------------------- Saving Images -------------------
    // TODO maybe move this to a IO class
    private void saveBitmapToGallery(Bitmap bitmap) {
        String filename = "Sony_" + selectedFilter + "_" + System.currentTimeMillis() + ".png";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveModern(bitmap, filename);
        } else {
            saveLegacy(bitmap, filename);
        }
    }

    private void saveModern(Bitmap bitmap, String filename) {

        //String filename = "Sony_" + System.currentTimeMillis() + ".png";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES);

        Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );

        if (uri == null) {
            Log.e("SAVE IMAGE", "Failed to create MediaStore entry");
            return;
        }

        try (OutputStream outputStream =
                     getContentResolver().openOutputStream(uri)) {

            if (outputStream == null) {
                Log.e("SAVE IMAGE", "Failed to open output stream");
                return;
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            Log.d("SAVE IMAGE", "Image saved (modern)!");

        } catch (Exception e) {
            Log.e("SAVE IMAGE", "Failed to save image", e);
        }
    }

    private void saveLegacy(Bitmap bitmap, String filename) {

        //String filename = "Sony_Filter_" + System.currentTimeMillis() + ".png";

        File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);

        // Ensure Pictures folder exists
        if (!picturesDir.exists()) {
            picturesDir.mkdirs();
        }

        File imageFile = new File(picturesDir, filename);

        try (FileOutputStream fos = new FileOutputStream(imageFile)) {

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);

            fos.flush();

            Log.d("SAVE IMAGE", "Image saved (legacy)!");

            // Make image appear in gallery
            Intent mediaScanIntent =
                    new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);

            Uri contentUri = Uri.fromFile(imageFile);

            mediaScanIntent.setData(contentUri);

            sendBroadcast(mediaScanIntent);

        } catch (Exception e) {
            Log.e("SAVE IMAGE", "Failed to save legacy image", e);
        }
    }

    // ------------------- Application of Filters -------------------
    /*
    private void generalFilterMethod() {
        if (currentImage == null) {
            Log.e("APPLY FILTER", "No image provided");
            return;
        }

        runAsync(()-> {
                    return ImageProcessor.toFILTERNAME(currentImage);
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                }
        );
    }

     */

    // Depreciated method
    private void applyFilterOfChoice(String filter, int k) {
        OnFilterDoneCallback onDone = () -> runOnUiThread(() -> setLoading(false));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (filter.equals("K-Means")) {
                Log.v("SEEK BAR", "k in k-means is " + k);
                applyKMeansThreaded(k, onDone);
            }
            else if (filter.equals("Pixelate")) {
                Log.v("SEEK BAR", "pixelation strength is " + k);
                applyPixelated(k, onDone);
            }
            else if (filter.equals("Grayscale")) {
                applyGrayScale(onDone);
            }
            else if (filter.equals("Interlaced")) {
                captureInterlaced(k, onDone);
            }
            else if (filter.equals("FloydSteinbergDithering")) {
                applyFloydSteinbergDithering(k, onDone);
            }
            else if (filter.equals("ColourBlind")) {
                applyColourBlind(k, onDone);
            }
            /*
            // Each method handles the UI filtered image display
            // ALWAYS return to UI thread at the end
            runOnUiThread(() -> setLoading(false));
             */
        });
        // Wrong place: Animators may only be run on Looper threads
        //setLoading(false);
    }

    // TODO Test this --> Seems to work fine
    private void applyConfigFilterOfChoice(String filter, FilterConfig config) {
        OnFilterDoneCallback onDone = () -> runOnUiThread(() -> setLoading(false));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {

            switch (filter) {

                case "K-Means":
                    Log.v("SEEK BAR", "k in k-means is " + config.getIntensity());
                    applyKMeansThreaded(config.getIntensity(), onDone);
                    break;
                case "Pixelate":
                    Log.v("SEEK BAR", "pixelation strength is " + config.getIntensity());
                    applyPixelated(config.getIntensity(), onDone);
                    break;
                case "Grayscale":
                    applyGrayScale(onDone);
                    break;
                case "Interlaced":
                    Log.v("SEEK BAR", "Interlaced option is " + config.getVariant());
                    Log.v("SEEK BAR", "Interlaced intensity is " + config.getIntensity());
                    captureInterlaced(config.getIntensity(), onDone);
                    break;
                case "FloydSteinbergDithering":
                    Log.v("SEEK BAR", "Dithering option is " + config.getVariant());
                    Log.v("SEEK BAR", "Dithering intensity is " + config.getIntensity());
                    applyFloydSteinbergDithering(config.getIntensity(), onDone);
                    break;
                case "ColourBlind":
                    Log.v("SEEK BAR", "Colour blind option is " + config.getVariant());
                    Log.v("SEEK BAR", "Colour blind intensity is " + config.getIntensity());
                    applyColourBlind(config.getIntensity(), onDone);
                    break;
            }
            /*
            if (filter.equals("K-Means")) {
                Log.v("SEEK BAR", "k in k-means is " + k);
                applyKMeansThreaded(k, onDone);
            }
            else if (filter.equals("Pixelate")) {
                Log.v("SEEK BAR", "pixelation strength is " + k);
                applyPixelated(k, onDone);
            }
            else if (filter.equals("Grayscale")) {
                applyGrayScale(onDone);
            }
            else if (filter.equals("Interlaced")) {
                captureInterlaced(k, onDone);
            }
            else if (filter.equals("FloydSteinbergDithering")) {
                applyFloydSteinbergDithering(k, onDone);
            }
            else if (filter.equals("ColourBlind")) {
                applyColourBlind(k, onDone);
            }
             */
            /*
            // Each method handles the UI filtered image display
            // ALWAYS return to UI thread at the end
            runOnUiThread(() -> setLoading(false));
             */
        });
        // Wrong place: Animators may only be run on Looper threads
        //setLoading(false);
    }


    // ------------------- Filters -------------------
    // ---- Kmeans
    private void applyKMeans() {

        if (currentImage == null) {
            Log.e("APPLY KMEANS", "No image provided");
            return;
        }

        ImageProcessor imgProcessor = new ImageProcessor();

        // 1. Extract pixels FROM ORIGINAL IMAGE
        List<float[]> points = imgProcessor.extractRGBValues(currentImage);

        int k = 10;
        // 2. Run KMeans
        KMeans kmeans = new KMeans(points, k);
        kmeans.run();

        // 3. Rebuild image from clusters (IMPORTANT STEP YOU'RE MISSING)
        Bitmap kMeansBMP = imgProcessor.rebuildFromClusters(
                currentImage.getWidth(),
                currentImage.getHeight(),
                points,
                kmeans.getCentroids(),
                kmeans.getAssignments()
        );

        // 4. Update UI image
        setCurrentImage(kMeansBMP);
    }

    private void applyKMeansThreaded(int k_for_kmeans, OnFilterDoneCallback onDone) {
        // No new executor needed — this method is already called from a worker thread

        if (currentImage == null) {
            Log.e("APPLY KMEANS", "No image provided");
            onDone.onDone(); // ← don't forget to unblock the button on early return too
            return;
        }
        runAsync(() -> {
                    // TODO this always crashes when not downscaling!
                    Bitmap img = currentImage;
                    if (downscaleEnabled) {
                        img = ImageProcessor.scaleBitmap(currentImage, 1000);
                    }

                    ImageProcessor imgProcessor = new ImageProcessor();
                    // Extract pixels
                    List<float[]> points = imgProcessor.extractRGBValues(img);

                    // Run KMeans (heavy work)
                    KMeans kmeans = new KMeans(points, k_for_kmeans);
                    kmeans.run();

                    // Rebuild image
                    return imgProcessor.rebuildFromClusters(img.getWidth(), img.getHeight(),
                            points, kmeans.getCentroids(), kmeans.getAssignments());
                },
                result -> {
                    // Update UI on main thread, then signal completion
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone(); // this activates: setLoading(false) only after image is displayed
                }
        );
    }

    private void applyKMeansThreadedNoLoading(int k_for_kmeans, OnFilterDoneCallback onDone) {

        if (currentImage == null) {
            Log.e("APPLY KMEANS", "No image provided");
            onDone.onDone();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {

            ImageProcessor imgProcessor = new ImageProcessor();

            // 1. Extract pixels
            List<float[]> points = imgProcessor.extractRGBValues(currentImage);

            //int k = 10;

            // 2. Run KMeans (heavy work)
            KMeans kmeans = new KMeans(points, k_for_kmeans);
            //setLoading(true);
            kmeans.run();

            // 3. Rebuild image
            Bitmap kMeansBMP;
            kMeansBMP = imgProcessor.rebuildFromClusters(currentImage.getWidth(), currentImage.getHeight(), points, kmeans.getCentroids(), kmeans.getAssignments());

            // 4. Update UI (must be on main thread)
            runOnUiThread(() -> {
                setCurrentImage(kMeansBMP);
                saveBitmapToGallery(kMeansBMP);
                //setLoading(false);
            });
        });
    }

    // ---- Greyscale
    private void applyGrayScale(OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY GREYSCALE", "No image provided");
            return;
        }

        runAsync(()-> {
                    Bitmap img = currentImage;
                    if (downscaleEnabled) {
                        img = ImageProcessor.scaleBitmap(currentImage, 1000);
                    }
                    return ImageProcessor.toGrayScale(img);
                    /*
                    Bitmap result = processWithDownscale(
                            currentImage,
                            500,
                            ImageProcessor::toGrayScale
                    );
                    return result;
                    */
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone();
                }
        );
    }


    // ---- Pixelated
    private void applyPixelated(int pixelationStrength, OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY PIXELATE", "No image provided");
            return;
        }
        runAsync(() -> {
                    Bitmap img = currentImage;
                    if (downscaleEnabled) {
                        img = ImageProcessor.scaleBitmap(currentImage, 1000);
                    }
                    ImageProcessor imgProcessor = new ImageProcessor();
                    return imgProcessor.pixelateImage(img, pixelationStrength);
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone();
                }
        );
    }

    private void applyPixelatedNoThread(int pixelationStrength) {

        if (currentImage == null) {
            Log.e("APPLY KMEANS", "No image provided");
            return;
        }

        ImageProcessor imgProcessor = new ImageProcessor();
        Bitmap pixelatedBitmap = imgProcessor.pixelateImage(currentImage, pixelationStrength);

        runOnUiThread(() -> {
            setCurrentImage(pixelatedBitmap);
            saveBitmapToGallery(pixelatedBitmap);
        });

    }

    // ---- Interlaced
    private void captureInterlaced(int delay, OnFilterDoneCallback onDone) {
        ImageProcessor imgProcessor = new ImageProcessor();
        // Dictates which row will be interlaced, every even row (2), or every 20th...
        int modValue = delay;
        takePhotoAsBitmap(bitmapA -> {

            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                takePhotoAsBitmap(bitmapB -> {

                    Bitmap imgA = bitmapA;
                    Bitmap imgB = bitmapB;
                    if (downscaleEnabled) {
                        imgA = imgProcessor.scaleBitmap(bitmapA, 1000);
                        imgB = imgProcessor.scaleBitmap(bitmapB, 1000);
                    }

                    //Bitmap resultInterlaced = imgProcessor.createInterlacedDistpacter(bitmapA, bitmapB, delay);
                    Bitmap resultInterlaced = imgProcessor.createInterlacedDistpacter(imgA, imgB, delay);

                    runOnUiThread(() -> {
                        currentImage = resultInterlaced;
                        setCurrentImage(resultInterlaced);
                        saveBitmapToGallery(resultInterlaced);
                        onDone.onDone();
                    });

                });
                // Tried delay * 500 --> too short for sony camera, try static ~1500ms
            }, 2 * 850);
        });


    }


    // ---- Dithering
    private void applyFloydSteinbergDithering(int kDitherOption, OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY GREYSCALE", "No image provided");
            return;
        }

        runAsync(()-> {
                    Bitmap img = currentImage;
                    if (downscaleEnabled) {
                        img = ImageProcessor.scaleBitmap(currentImage, 1000);
                    }
                    return ImageProcessor.createDitheringDistpacter(img, kDitherOption);
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone();
                }
        );
    }


    // ---- Colourblind
    private void applyColourBlind(int kBlind, OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY GREYSCALE", "No image provided");
            return;
        }

        runAsync(()-> {
                    Bitmap img = currentImage;
                    if (downscaleEnabled) {
                        img = ImageProcessor.scaleBitmap(currentImage, 1000);
                    }
                    return ImageProcessor.toColourBlind(img, kBlind);
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone();
                }
        );
    }


    /*
    private void applyKMeans() {
        if (currentImage == null) {
            Log.e("APPLY KMEANS", "No image provided");
            return;
        }
        ImageProcessor imgProcessor = new ImageProcessor();

        Bitmap bmp = imgProcessor.imageToBitmap(currentImage);
        ArrayList<float[]> points = imgProcessor.extractRGBValues(bmp);
        imgProcessor.setPoints(points);


        KMeans kmeans = new KMeans(points, k);
        kmeans.run();


        setCurrentImage(kmeansBMP);
    }
    */











}