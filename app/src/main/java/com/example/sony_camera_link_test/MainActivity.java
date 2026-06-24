package com.example.sony_camera_link_test;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;

import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
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
    private AppColour appColor; // is actually used

    private ImageView imageView;

    private Spinner filterSpinner;
    private SeekBar seekBarFilterStrength;
    private TextView seekValueLabel;
    private TextView seekFiterStrOptLabel;
    private TextView filterInfoTextCard;
    private TextView subFilterInfoTextCard;

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
            //"Grayscale", // moved into colourblind
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

    // TODO any commented out var is unneeded or moved to AndroidCameraClient
    private SonyCameraClient sonyCameraClient;
    private AndroidCameraClient cameraClient;

    private ImageCapture imageCapture;
    // TODO make the preview veiw
    private PreviewView previewView;

    // Special list holding the available cameras
    private ArrayAdapter<CameraOption> cameraAdapter;

    // Holds the currently active camera configuration
    private CameraOption activeCameraOption;


    // Default lens is back
    private int currentLensFacing = CameraSelector.LENS_FACING_BACK;
    //private ProcessCameraProvider cameraProvider; // initialized and used in setupCamera

    // Store the exact camera ID we want to use
    // private String selectedLogicalCameraId = "0";

    // Current camera active
    private Camera camera; // initialized in bindCameraUseCases

    //private List<CameraOption> availableCamerasList = new ArrayList<>();
    //private ArrayAdapter<CameraOption> cameraAdapter;


    // ── Camera Core & UI State
    // private ProcessCameraProvider cameraProvider;
    // private ImageCapture imageCapture;

    //private List<CameraOption> availableCamerasList = new ArrayList<>();


    // Then handle the result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //setupCamera();
            startCameraPipeline();
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

        sonyCameraClient = new SonyCameraClient();

        cameraClient = new AndroidCameraClient(this, this, imageView, getActivityResultRegistry());

        // Checks the version so that the proper save function is called
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
        }

        try {
            bindViews();
            setupFilterSpinner();
            currentFilterConfig = new FilterConfig(currentIntensity, null);
            setupSeekBar();
            setupButtons();
            setupSideMenus();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                //setupCamera();
                startCameraPipeline();
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
        filterInfoTextCard = findViewById(R.id.filter_info);

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

                Log.d("FILTER_SPINNER_CHANGE", "selected filter is: " + selectedFilter);

                showInfoCard(selectedFilter);

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
                    /* Moved to colour blind
                    case 2: // Grayscale — 1–2 (no real range needed)
                        //currentFilterConfig.setVariant(null);
                        currentFilterConfig.setVariant(ColourBlindFilterOption.GRAYSCALE);
                        setSeekBarRange(1, 2);
                        updateSeekBarTicks("", "", "");
                        updateFilterOptionLabel("");
                        break;

                     */
                    case 2: // Interlace — 0–7
                        currentFilterConfig.setVariant(InterlaceFilterOption.VERTICAL_STRIPES);
                        setSeekBarRange(0, 7);
                        updateSeekBarTicks("0", "", "7");
                        updateFilterOptionLabel("OPTION");
                        break;
                    case 3: // FloydSteinbergDithering — 0–5
                        currentFilterConfig.setVariant(DitherFilterOption.useFloydSteinbergDitheringOption2);
                        setSeekBarRange(0, 6);
                        updateSeekBarTicks("0", "", "6");
                        updateFilterOptionLabel("OPTION");
                        break;
                    case 4: // colour blind — 0–6 options
                        currentFilterConfig.setVariant(ColourBlindFilterOption.PROTANOPIA);
                        setSeekBarRange(0,6);
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

    // ── Filter Strength SeekBar ───────────────────────────────────────────
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
            if (selectedFilter.equals("K-Means")) {
                showSubInfoCard(FilterInfo.K_MEANS, 0);
            }
            if (selectedFilter.equals("Pixelate")) {
                showSubInfoCard(FilterInfo.PIXELATE, 0);
            }
            return String.valueOf(config.getIntensity());
        }

        if (selectedFilter.equals("Interlaced")) {
            InterlaceFilterOption mode = (InterlaceFilterOption) config.getVariant();
            switch (mode) {
                case CHECKERED:
                    showSubInfoCard(FilterInfo.INTERLACED, 0);
                    return "Checkered";
                case VERTICAL_STRIPES:
                    showSubInfoCard(FilterInfo.INTERLACED, 1);
                    return "Vertical Stripes";
                case HALF_HALF:
                    showSubInfoCard(FilterInfo.INTERLACED, 2);
                    return "Half / Half";
                case NOISE:
                    showSubInfoCard(FilterInfo.INTERLACED, 3);
                    return "Noise";
                case SWIRL:
                    showSubInfoCard(FilterInfo.INTERLACED, 4);
                    return "Swirl";
                case GRID_PATTERN:
                    showSubInfoCard(FilterInfo.INTERLACED, 5);
                    return "Grid";
            }
        }

        if (selectedFilter.equals("FloydSteinbergDithering")) {
            DitherFilterOption mode = (DitherFilterOption) config.getVariant();
            switch (mode) {
                case useFloydSteinbergDitheringOption1:
                    showSubInfoCard(FilterInfo.FLOYD_STEINBERG, 0);
                    return "Floyd-Steinberg A";
                case useFloydSteinbergDitheringOption2:
                    showSubInfoCard(FilterInfo.FLOYD_STEINBERG, 1);
                    return "Floyd-Steinberg B";
                case glitchyDither:
                    showSubInfoCard(FilterInfo.FLOYD_STEINBERG, 2);
                    return "Glitchy Dither";
                case deepFried:
                    showSubInfoCard(FilterInfo.FLOYD_STEINBERG, 3);
                    return "Deep Fried";
                case spookyDither:
                    showSubInfoCard(FilterInfo.FLOYD_STEINBERG, 4);
                    return "Spooky Dark Dither";
            }
        }

        if (selectedFilter.equals("ColourBlind")) {
            ColourBlindFilterOption mode = (ColourBlindFilterOption) config.getVariant();
            switch (mode) {
                case PROTANOPIA:
                    showSubInfoCard(FilterInfo.COLOUR_BLIND, 0);
                    return "Protanopia";
                case DEUTERANOPIA:
                    showSubInfoCard(FilterInfo.COLOUR_BLIND, 1);
                    return "Deuteranopia";
                case TRITANOPIA:
                    showSubInfoCard(FilterInfo.COLOUR_BLIND, 2);
                    return "Tritanopia";
                case DOG_SIMULATION:
                    showSubInfoCard(FilterInfo.COLOUR_BLIND, 3);
                    return "Dog Vision";
                case GRAYSCALE:
                    showSubInfoCard(FilterInfo.COLOUR_BLIND, 4);
                    return "Grayscale";
            }
        }

        /*
        if (selectedFilter.equals("Grayscale")) {
            return "Grayscale";
        }
         */

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

    // --------- Info cards
    private void showInfoCard(String activeFilter) {
        // Fixed: changed 'selectedFilter' to 'activeFilter' to match the method argument
        Log.d("INFO_CARD", "selected filter is: " + activeFilter);

        // 1. Find the target TextView directly within your activity layout
        TextView filterInfoTextCard = findViewById(R.id.filter_info);

        if (filterInfoTextCard != null) {
            String title;
            String description;

            // 2. Map the incoming option String to its respective title and description
            switch (activeFilter) {
                case "K-Means":
                    title = FilterInfo.K_MEANS.getTitle();
                    description = FilterInfo.K_MEANS.getDescription();
                    break;
                case "Pixelate":
                    title = FilterInfo.PIXELATE.getTitle();
                    description = FilterInfo.PIXELATE.getDescription();
                    break;
                    /*
                case "Grayscale":
                    title = "Grayscale Mode";
                    description = "Converts RGB channels into monochromatic luminance values, completely stripping color saturation.";
                    break;
                     */
                case "Interlaced":
                    title = FilterInfo.INTERLACED.getTitle();
                    description = FilterInfo.INTERLACED.getDescription();
                    break;
                case "FloydSteinbergDithering":
                    title = FilterInfo.FLOYD_STEINBERG.getTitle();
                    description = FilterInfo.FLOYD_STEINBERG.getDescription();
                    break;
                case "ColourBlind":
                    title = FilterInfo.COLOUR_BLIND.getTitle();
                    description = FilterInfo.COLOUR_BLIND.getDescription();
                    break;
                default:
                    title = "Unknown Filter";
                    description = "No description details available for this selection.";
                    break;
            }

            // 3. Format the text and push it into your styled layout card
            String detailedInfo = title + "\n" + description;
            filterInfoTextCard.setText(detailedInfo);

        } else {
            Log.e("MAIN_CAMERA_DEBUG", "Error: filter_info TextView not found in current layout view hierarchy.");
        }
    }

    private void showSubInfoCard(FilterInfo filterInfo, int option) {
        Log.d("INFO_CARD", "selected filter is: " + filterInfo.getTitle());

        TextView subFilterInfoTextCard = findViewById(R.id.subfilter_info);

        String subFilterText = filterInfo.getSubFilter(option);

        subFilterInfoTextCard.setText(subFilterText);
    }

    // ── Buttons ───────────────────────────────────────────────────────────
    private void setupButtons() {

        /*
        // Sony Camera button: launch camera
        buttonPhoto.setOnClickListener(v -> {
            takePhotoAsBitmapSony(bitmap -> {
                setCurrentImage(bitmap);
            });
        });

        // Default phone Camera button (backfacing)
        buttonPhoneCamera.setOnClickListener(v -> {
            cameraClient.takePhotoAsBitmap(rotatedBitmap -> {
                setCurrentImage(rotatedBitmap);
            });
        });

         */

        buttonPhoneCamera.setOnClickListener(v -> {
            if (activeCameraOption == null) {
                Log.w("MAIN_CAMERA_DEBUG", "Capture aborted: No camera selected.");
                return;
            }

            // Route to the appropriate isolated helper function
            if ("666".equals(activeCameraOption.logicalId)) {
                Log.w("MAIN_CAMERA_DEBUG", "Capture with sony camera");
                captureSonyPhoto();

            } else if ("999".equals(activeCameraOption.logicalId)) {
                Log.w("MAIN_CAMERA_DEBUG", "Capture with system camera");
                captureSystemFallbackPhoto();
            } else {
                Log.w("MAIN_CAMERA_DEBUG", "Capture with CameraX camera");
                captureInternalCameraXPhoto();
            }
        });

        // Apply filter button
        buttonProcess.setOnClickListener(v -> {
            applyFilter(selectedFilter, currentIntensity);
        });

        /*
        // Old method to switch the selected camera
        // Camera type menu
        switchCameraFacingButton.setOnClickListener(v -> {
            Log.d("SETUP BUTTONS", "Binding camera, currentLensFacing is " + currentLensFacing);
        });
        // Set colour for the Camera type button
        switchCameraFacingButton.setBackgroundTintList(
                ColorStateList.valueOf(appColor.MEDIUM_PURPLE.getColor(this)));
         */

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

    private void startCameraPipeline() {
        Boolean debug = false;

        if (debug) Log.d("MAIN_CAMERA_DEBUG", "startCameraPipeline() called. Initiating asynchronous camera setup...");
        // Initialize logic asynchronously
        cameraClient.setupCamera(cameras -> {
            if (debug) Log.d("MAIN_CAMERA_DEBUG", "setupCamera callback triggered. Total hardware cameras loaded: " + (cameras != null ? cameras.size() : 0));

            if (cameras == null || cameras.isEmpty()) {
                Log.w("MAIN_CAMERA_DEBUG", "Warning: Available cameras list is empty or null!");
            }

            // This runs as a callback once the hardware list is generated
            cameraAdapter = new ArrayAdapter<CameraOption>(MainActivity.this, android.R.layout.simple_spinner_item, cameras); // This line has a red line error: "Cannot resolve constructor 'ArrayAdapter(MainActivity, int, <lambda parameter>)'"
            cameraAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            switchCameraFacingSpinner.setAdapter(cameraAdapter);

            setupSpinnerListener(cameras);

            // Set default selection natively on the UI
            int defaultIndex = cameraClient.setDefaultCamera(0);
            if (debug) Log.d("MAIN_CAMERA_DEBUG", "Setting default spinner selection to index: " + defaultIndex);
            switchCameraFacingSpinner.setSelection(cameraClient.setDefaultCamera(defaultIndex));
        });
    }

    // Not needed any more just kept here for reference?
    /*
    public void setupCameraSpinner() {
        cameraAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, availableCamerasList);
        cameraAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        switchCameraFacingSpinner.setAdapter(cameraAdapter);

        switchCameraFacingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                activeCameraOption = availableCamerasList.get(position);

                if (activeCameraOption.isSystemFallback) {
                    //cameraClient.openSystemCameraApp();
                    return;
                }

                Toast.makeText(MainActivity.this, activeCameraOption.label + " Activated", Toast.LENGTH_SHORT).show();
                Log.d("SETUP_CAMERA_DEBUG", "The position is: " + position);
                Log.d("SETUP_CAMERA_DEBUG", "The camera is: " + activeCameraOption.label + " | "  + activeCameraOption.logicalId + " | " + activeCameraOption.facing + " | ");
                // Fire your internal CameraX pipeline execution
                //bindCameraUseCases();
                //bindCameraUseCasesOG();
                cameraClient.logCameraState("BINDING BEFORE");
                cameraClient.bindCameraUseCasesBySelection(activeCameraOption);
                cameraClient.logCameraState("BINDING AFTER");

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
     */

    private void setupSpinnerListener(List<CameraOption> cameras) {
        Boolean debug = true;

        switchCameraFacingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CameraOption selected = cameras.get(position);
                activeCameraOption = selected;
                if (debug) Log.d("SETUP_CAMERA_DEBUG", "The camera is: " + selected.label + " | "  + selected.logicalId + " | " + selected.facing + " | ");

                // System Camera selected
                if (selected.isSystemFallback || (selected.logicalId.equals("999"))) {
                    if (debug) Log.d("SETUP_CAMERA_DEBUG", "System Camera selcted");

                    /*
                    cameraClient.openSystemCameraApp(correctedBitmap -> {
                        if (debug)
                            Log.d("SETUP_CAMERA_DEBUG", "High-res system image received! Displaying on UI.");
                        setCurrentImage(correctedBitmap);
                    });

                     */

                    // Sony Camera selected
                    /*
                } if (selected.isSystemFallback || (selected.logicalId.equals("666"))) {
                    if (debug) Log.d("SETUP_CAMERA_DEBUG", "System Camera selcted");

                    sonyCameraClient.takePicture(OnPictureTakenListener -> {
                        takePhotoAsBitmapSony(OnPictureTakenListener);
                        //setCurrentImage();
                    });

                     */

                    // other front or back camera
                }
                if (selected.logicalId.equals("666")) {
                    if (debug) Log.d("SETUP_CAMERA_DEBUG", "Sony Camera selcted");

                } else {
                    cameraClient.bindCameraUseCasesBySelection(selected);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void captureSonyPhoto() {
        Log.d("MAIN_CAMERA_DEBUG", "Triggering Sony External API capture...");
        takePhotoAsBitmapSonyOG(bitmap -> {
            setCurrentImage(bitmap);
        });
    }

    private void captureSystemFallbackPhoto() {
        Log.d("MAIN_CAMERA_DEBUG", "Launching native system camera application intent...");
        cameraClient.openSystemCameraApp(correctedBitmap -> {
            setCurrentImage(correctedBitmap);
        });
    }

    private void captureInternalCameraXPhoto() {
        Log.d("MAIN_CAMERA_DEBUG", "Capturing frame natively via CameraX pipeline...");
        cameraClient.takePhotoAsBitmap(rotatedBitmap -> {
            Log.d("MAIN_DEBUG", "Bitmap received in MainActivity! Displaying in ImageView.");
            setCurrentImage(rotatedBitmap);
        });
    }


    private void setupZoomSeekBar() {
        if (zoomSeekBar == null) return;

        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                camera = cameraClient.getCamera();
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

    // ---------- Filter seek bar methods
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


    // ----------- Taking Photos ---------------------------------------------
    private void takePhotoAsBitmapOLD(OnBitmapReady callback) {
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

    private void takePhotoAsBitmapSony(String imageUrl, OnBitmapReady callback) {
        Log.d("MAIN_CAMERA_DEBUG", "Forwarding download request to shared Sony camera client...");

        isCameraCapturing = true;
        // 1. You MUST call takePicture first to wake up the camera hardware
        sonyCameraClient.takePicture(new SonyCameraClient.OnPictureTakenListener() {
            @Override
            public void onSuccess(String imageUrl) {
                Log.d("MAIN_CAMERA_DEBUG", "Step 2: Shutter success! Got URL from server: " + imageUrl);

                // 2. CRITICAL: Only call the downloader INSIDE this success block
                // using the exact 'imageUrl' string provided by the camera!
                runOnUiThread(() -> {
                    takePhotoAsBitmapSony(imageUrl, bitmap -> {
                        Log.d("MAIN_CAMERA_DEBUG", "Step 3: Bitmap downloaded. Rendering to UI.");
                        currentImage = bitmap;
                        imageView.setImageBitmap(bitmap);
                        isCameraCapturing = false;
                    });
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e("MAIN_CAMERA_DEBUG", "Sony hardware failed to snap photo", e);
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Camera hardware error", Toast.LENGTH_SHORT).show()
                );
            }
        });
        /*
        // Call the newly implemented helper method in your client class
        sonyCameraClient.downloadBitmap(imageUrl, new SonyCameraClient.OnBitmapReadyListener() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                Log.d("MAIN_CAMERA_DEBUG", "Sony client successfully delivered bitmap asset.");
                // Already safely on the UI thread due to the handler inside SonyCameraClient!
                callback.onReady(bitmap);
            }

            @Override
            public void onError(Exception e) {
                Log.e("MAIN_CAMERA_DEBUG", "Failed to retrieve Sony bitmap via client framework", e);
                Toast.makeText(MainActivity.this, "Failed to download image from camera", Toast.LENGTH_SHORT).show();
            }
        });

         */
    }

    // TODO figure out how to move this method to SonyCameraClient
    private void takePhotoAsBitmapSonyOG(OnBitmapReady callback) {
        sonyCameraClient.takePicture(new SonyCameraClient.OnPictureTakenListener() {

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

    // Needed for Interlace
    //TODO try and see if this method can be used in both interalce and regular button presses
    private void takeSinglePhotoAsBitmap(OnBitmapReady callback) {
        if (activeCameraOption == null) {
            Log.w("MAIN_CAMERA_DEBUG", "Cannot capture bitmap: No camera active.");
            return;
        }

        // SOURCE 1: Sony External Camera
        if ("666".equals(activeCameraOption.logicalId)) {
            takePhotoAsBitmapSonyOG(callback);
            //captureSonyPhoto();
        }

        // SOURCE 2: System Camera Fallback App (Intent Launcher)
        else if ("999".equals(activeCameraOption.logicalId)) {
            cameraClient.openSystemCameraApp(callback::onReady);
        }

        // SOURCE 3: Internal CameraX Framework
        else {
            cameraClient.takePhotoAsBitmap(callback::onReady);
        }
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
            //progressBar.setVisibility(View.GONE);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    // ── Async work ──────────────────────────────────────────────
    private void runAsync(Supplier<Bitmap> background, Consumer<Bitmap> onDone) {
        // background: the heavy work — runs on worker thread, returns a Bitmap
        // onDone:     UI update — runs on main thread after background finishes
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
    /**
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
    }**/

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

        // 1. Capture the first frame from whichever camera is active
        takeSinglePhotoAsBitmap(bitmapA -> {
            Log.d("INTERLACE_DEBUG", "First frame captured successfully.");

            // Wait out your configured delay sequence
            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                // 2. Capture the second frame from the same active camera
                takeSinglePhotoAsBitmap(bitmapB -> {
                    Log.d("INTERLACE_DEBUG", "Second frame captured successfully. Processing...");

                    Bitmap imgA = bitmapA;
                    Bitmap imgB = bitmapB;

                    if (downscaleEnabled) {
                        imgA = imgProcessor.scaleBitmap(bitmapA, 1000);
                        imgB = imgProcessor.scaleBitmap(bitmapB, 1000);
                    }

                    // Compile the interlaced row modifications
                    Bitmap resultInterlaced = imgProcessor.createInterlacedDistpacter(imgA, imgB, delay);

                    runOnUiThread(() -> {
                        currentImage = resultInterlaced;
                        setCurrentImage(resultInterlaced);
                        saveBitmapToGallery(resultInterlaced);
                        onDone.onDone();
                    });
                });

            }, 2 * 850); // ~1700ms delay between frames
        });
    }

    // Only works with CameraX
    private void captureInterlacedOld(int delay, OnFilterDoneCallback onDone) {
        ImageProcessor imgProcessor = new ImageProcessor();
        // Dictates which row will be interlaced, every even row (2), or every 20th...
        int modValue = delay;
        cameraClient.takePhotoAsBitmap(bitmapA -> {

            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                cameraClient.takePhotoAsBitmap(bitmapB -> {

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


}