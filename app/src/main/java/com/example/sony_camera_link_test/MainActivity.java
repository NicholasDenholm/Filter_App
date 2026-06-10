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

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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
    private SeekBar seekBar;
    private TextView seekValueLabel;
    private Button buttonPhoto;
    private Button buttonPhoneCamera;
    private Button buttonProcess;
    private ProgressBar progressBar;
    private MaterialButton switchCameraFacingButton;
    private MaterialButton downscaleImageButton;

    // ── State ──────────────────────────────────────────────────────────────
    // Default on startup
    private String selectedFilter = "K-means";
    private int currentIntensity = 10;
    private FilterConfig currentFilterConfig;

    private Bitmap currentImage;
    private String CurrentImageUrl; // For the Sony camera

    // Store the exact camera ID we want to use
    private String selectedLogicalCameraId = null;
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

    // Track the minimum offset manually instead of using SeekBar.setMin() (API 26+).
    // The SeekBar internally always runs from 0; add seekMin when reading progress.
    private int seekMin = 0;

    // Helper: set the seekbar range without using setMin/getMin
    private void setSeekBarRange(int min, int max) {
        seekMin = min;
        seekBar.setMax(max - min);      // internal max is always (real max − real min)
    }

    // Helper: get the real value (adds the offset back)
    private int getSeekBarValue() {
        return seekBar.getProgress() + seekMin;
    }

    // Helper: set the real value (subtracts the offset)
    private void setSeekBarValue(int value) {
        seekBar.setProgress(value - seekMin);
    }


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
        setContentView(R.layout.ui_redesign); // This is the new UI design

        cameraClient = new SonyCameraClient();

        // Checks the version so that the proper save function is called
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
        }

        // Came with the default template
        /*
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
         */

        try {
            //  Do the setup
            bindViews();
            setupFilterSpinner();
            currentFilterConfig = new FilterConfig(currentIntensity, null);
            setupSeekBar();
            setupButtons();

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


        /*
        // ------------------- Spinner menu
        Spinner spinner = findViewById(R.id.filter_spinner);
        String[] filters = {"K-Means", "Pixelate", "Grayscale", "Interlaced"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filters);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                selectedFilter = filters[position];

                Log.d("FILTER", "Selected: " + selectedFilter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
         */

        // ------------------- Seek bar
        //SeekBar seekBar = findViewById(R.id.seekBar2);

        // ------------------- Take photo Button
        /*
        Button takePhotoButton = findViewById(R.id.button_photo);
        takePhotoButton.setOnClickListener(v -> {

            takePhotoAsBitmap(bitmap -> {

                currentImage = bitmap;

                ImageView imageView = findViewById(R.id.image_view);
                imageView.setImageBitmap(bitmap);
            });
        });

         */

        // ------------------- Process Photo Button
        //Button processPhotoButton = findViewById(R.id.button_process);
        // dynamic filter application
        /*
        processPhotoButton.setOnClickListener(v -> {
            int seekBarValue = seekBar.getProgress();
            applyFilterOfChoice(selectedFilter, seekBarValue);
        });

         */

        /*
        // only greyscale
        processPhotoButton.setOnClickListener(v -> applyFilter());

        // Only Kmeans
        processPhotoButton.setOnClickListener(v -> {
            int k_for_kmeans = seekBar.getProgress();
            Log.d("SEEK BAR", "Seek bar value is " + k_for_kmeans);
            applyKMeansThreaded(k_for_kmeans);
        });
        // Only pixalation
        processPhotoButton.setOnClickListener(v -> {
            int pixelationStrength = seekBar.getProgress();
            Log.d("SEEK BAR", "Seek bar value is " + pixelationStrength);
            applyPixelated(pixelationStrength);
        });
        */
    }

    // ── Bind all views from the layout ────────────────────────────────────
    private void bindViews() {
        imageView = findViewById(R.id.image_view);
        filterSpinner = findViewById(R.id.filter_spinner);
        seekBar = findViewById(R.id.seekBar2);
        seekValueLabel = findViewById(R.id.seek_value_label);
        buttonPhoto = findViewById(R.id.button_photo);
        buttonPhoneCamera = findViewById(R.id.button_phone_camera);
        buttonProcess = findViewById(R.id.button_process);
        progressBar = findViewById(R.id.progressBar);
        switchCameraFacingButton = findViewById(R.id.button_switch_camera);
        downscaleImageButton = findViewById(R.id.button_scale_image_down);
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
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(0xFFDDDDDD); // light gray
                ((TextView) view).setTextSize(13);
                return view;
            }

            // Style the dropdown list items
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(0xFFDDDDDD);
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
                        setSeekBarRange(0, 20);
                        break;
                    case 1: // Pixelation — block size 2–40px
                        currentFilterConfig.setVariant(null);
                        setSeekBarRange(0, 40);
                        break;
                    case 2: // Grayscale — 1–2 (no real range needed)
                        currentFilterConfig.setVariant(null);
                        setSeekBarRange(1, 2);
                        break;
                    case 3: // Interlace — 0–7
                        currentFilterConfig.setVariant(InterlaceFilterOption.VERTICAL_STRIPES);
                        setSeekBarRange(0, 7);
                        break;
                    case 4: // FloydSteinbergDithering — 0–5
                        currentFilterConfig.setVariant(DitherFilterOption.useFloydSteinbergDitheringOption2);
                        setSeekBarRange(0, 6);
                        break;
                    case 5: // colour blind — 0–5 options?
                        currentFilterConfig.setVariant(ColourBlindFilterOption.PROTANOPIA);
                        setSeekBarRange(0,5);
                        break;
                    default:
                        setSeekBarRange(0, 20);
                        break;
                }

                // Reset to midpoint and update label
                int mid = seekMin + (seekBar.getMax() / 2);
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
    private void setupSeekBar() {
        // Set initial label to match the XML default progress of 10
        //seekValueLabel.setText(String.valueOf(seekBar.getProgress()));
        // used to ensure backwards compatibility
        //currentIntensity = getSeekBarValue();

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update the large purple number in real time
                currentIntensity = getSeekBarValue();
                // update central config
                currentFilterConfig.setIntensity(currentIntensity);
                // Choose the option to update the label
                //seekValueLabel.setText(String.valueOf(progress));
                updateVariantFromIntensity();
                seekValueLabel.setText(formatSeekBarLabel(currentFilterConfig));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
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

        // Default fallback
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

        // Camera button: launch camera
        buttonPhoto.setOnClickListener(v -> {
            // TODO: replace with your existing camera / gallery intent logic
            // Example:
            // Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            takePhotoAsBitmapSony(bitmap -> {
                currentImage = bitmap;
                ImageView imageView = findViewById(R.id.image_view);
                imageView.setImageBitmap(bitmap);
            });
        });

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

        Log.d("SETUP BUTTONS", "Binding camera, currentLensFacing is " + currentLensFacing);
        switchCameraFacingButton.setOnClickListener(v -> {
            //switchCamera();
            showCameraMenu();
            //openSystemCameraApp();
        });
        // Set colour for the default option
        switchCameraFacingButton.setBackgroundTintList(
                ColorStateList.valueOf(appColor.MEDIUM_PURPLE.getColor(this)));

        downscaleImageButton.setOnClickListener(v -> changeDownScaleOption() );
        // Set colour for the default option
        downscaleImageButton.setBackgroundTintList(
                ColorStateList.valueOf(appColor.WHITE.getColor(this)));
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

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(currentLensFacing).build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
        } catch (Exception e) {
            Log.e("CAMERA BIND", "Failed to bind camera", e);
        }

    }

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

    @androidx.annotation.OptIn(markerClass = {ExperimentalCamera2Interop.class, ExperimentalCamera2Interop.class})
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

                // 2. Wrap the file in a secure content URI using the provider we defined in Manifest
                photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        imageFile);

                // 3. Tell the Samsung/System camera to drop the full image data into this URI location
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                cameraIntentLauncher.launch(takePictureIntent);

            } catch (IOException ex) {
                Log.e("CAMERA_INTENT", "Error creating image storage file", ex);
                Toast.makeText(this, "Could not initialize file storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

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


    // ── Filter application ────────────────────────────────────────────────
    private void applyFilter(String filter, int intensity) {
        // Show spinner, disable button while processing
        //progressBar.setVisibility(View.VISIBLE);
        //buttonProcess.setEnabled(false);

        // Trying just old method instead of async
        setLoading(true);
        applyFilterOfChoice(filter, intensity);

        // TODO: run your actual filter processing here (ideally in an AsyncTask
        // or coroutine so the UI thread isn't blocked).
        // Example stub using a Handler to simulate async work:
        /*
        imageView.postDelayed(() -> {
            // -- swap in your processed bitmap here --
            // imageView.setImageBitmap(processedBitmap);

            progressBar.setVisibility(View.GONE);
            buttonProcess.setEnabled(true);
        }, 500);

         */
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
                        .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {

                            @Override
                            public void onResourceReady(Bitmap resource,
                                                        com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                                callback.onReady(resource);
                            }

                            @Override
                            public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {
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

    // TODO Test this
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