package com.example.sony_camera_link_test;

import static com.example.sony_camera_link_test.SonyCameraClient.CAMERA_URL;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class MainActivity extends AppCompatActivity {

    // ── UI references ──────────────────────────────────────────────────────
    private ImageView imageView;
    private Spinner filterSpinner;
    private SeekBar seekBar;
    private TextView seekValueLabel;
    private Button buttonPhoto;
    private Button buttonPhoneCamera;
    private Button buttonProcess;
    private ProgressBar progressBar;

    // ── State ──────────────────────────────────────────────────────────────
    // Default on startup
    private String selectedFilter = "K-means";
    private int currentIntensity = 10;

    private Bitmap currentImage;
    private String CurrentImageUrl;

    private boolean isCameraCapturing = false;

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

    // ── Camera Clients ─────────────────────────────────────────────────────
    private SonyCameraClient cameraClient;
    private ImageCapture imageCapture;


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
        setContentView(R.layout.ui_redesign);

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

                // Update the seekbar label to reflect what the value means
                // for each filter type (clusters for k-means, block size for pixelation, etc.)
                switch (position) {
                    case 0: // K-means — cluster count 2–22
                        setSeekBarRange(2, 22);
                        break;
                    case 1: // Pixelation — block size 2–40px
                        setSeekBarRange(2, 40);
                        break;
                    case 2: // Grayscale — 1–2 (no real range needed)
                        setSeekBarRange(1, 2);
                        break;
                    case 3: // Interpolation — 0–5
                        setSeekBarRange(2, 10);
                        break;
                    case 4: // FloydSteinbergDithering — 0–5
                        setSeekBarRange(2, 10);
                        break;
                    case 5:
                        setSeekBarRange(0,5);
                        break;
                    default:
                        setSeekBarRange(2, 22);
                        break;
                }

                // Reset to midpoint and update label
                int mid = seekMin + (seekBar.getMax() / 2);
                setSeekBarValue(mid);
                seekValueLabel.setText(String.valueOf(mid));
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
        currentIntensity = getSeekBarValue();

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update the large purple number in real time
                currentIntensity = getSeekBarValue();
                seekValueLabel.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }


    // ── Buttons ───────────────────────────────────────────────────────────
    private void setupButtons() {

        // Camera button: launch camera or photo picker
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

        // Apply filter button
        buttonProcess.setOnClickListener(v -> {
            applyFilter(selectedFilter, currentIntensity);
        });

        buttonPhoneCamera.setOnClickListener(v -> {
            takePhotoAsBitmap(bitmap -> {
                currentImage = bitmap;
                imageView.setImageBitmap(bitmap);
            });
        });
    }


    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                imageCapture = new ImageCapture.Builder().build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,                                    // lifecycle owner
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageCapture
                );
            } catch (Exception e) {
                Log.e("CAMERA SETUP", "Failed to bind camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
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

    private void applyGrayScale(OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY GREYSCALE", "No image provided");
            return;
        }

        runAsync(()-> {
                    return ImageProcessor.toGrayScale(currentImage);
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone();
                }
        );
    }

    private void applyFloydSteinbergDithering(int kDither, OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY GREYSCALE", "No image provided");
            return;
        }

        runAsync(()-> {
                    Bitmap scaled = scaleBitmap(currentImage, 800);
                    //return ImageProcessor.deepFriedEffect(scaled);
                    //return ImageProcessor.dither(scaled);
                    return ImageProcessor.createDitheringDistpacter(scaled, kDither);
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone();
                }
        );
    }

    private void applyColourBlind(int kBlind, OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY GREYSCALE", "No image provided");
            return;
        }

        runAsync(()-> {
                    Bitmap scaled = scaleBitmap(currentImage, 800);
                    return ImageProcessor.toColourBlind(scaled, kBlind);
                },
                result -> {
                    setCurrentImage(result);
                    saveBitmapToGallery(result);
                    onDone.onDone();
                }
        );
    }

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
                    // worker thread — your existing logic unchanged
                    Bitmap scaled = scaleBitmap(currentImage, 1000);
                    ImageProcessor imgProcessor = new ImageProcessor();
                    // Extract pixels
                    List<float[]> points = imgProcessor.extractRGBValues(scaled);

                    // Run KMeans (heavy work)
                    KMeans kmeans = new KMeans(points, k_for_kmeans);
                    kmeans.run();

                    // Rebuild image
                    return imgProcessor.rebuildFromClusters(scaled.getWidth(), scaled.getHeight(), points, kmeans.getCentroids(), kmeans.getAssignments());
                    },
                    result -> {
                        // Update UI on main thread, then signal completion
                        setCurrentImage(result);
                        saveBitmapToGallery(result);
                        onDone.onDone(); // this activates: setLoading(false) only after image is displayed
        }
        );
    }

    private Bitmap scaleBitmap(Bitmap source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        if (scale >= 1f) return source; // already small enough, don't upscale
        return Bitmap.createScaledBitmap(source,
                Math.round(width * scale),
                Math.round(height * scale), true);
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

    private void applyPixelated(int pixelationStrength, OnFilterDoneCallback onDone) {
        if (currentImage == null) {
            Log.e("APPLY PIXELATE", "No image provided");
            return;
        }
        runAsync(() -> {
                ImageProcessor imgProcessor = new ImageProcessor();
                return imgProcessor.pixelateImage(currentImage, pixelationStrength);
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

    private void captureInterlaced(int delay, OnFilterDoneCallback onDone) {
        ImageProcessor imgProcessor = new ImageProcessor();
        // Dictates which row will be interlaced, every even row (2), or every 20th...
        int modValue = delay;
        takePhotoAsBitmap(bitmapA -> {

            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                takePhotoAsBitmap(bitmapB -> {

                    //Bitmap resultInterlaced = imgProcessor.createInterlaced(bitmapA, bitmapB, delay);
                    Bitmap resultInterlaced = imgProcessor.createInterlacedDistpacter(bitmapA, bitmapB, delay);

                    runOnUiThread(() -> {
                        currentImage = resultInterlaced;
                        setCurrentImage(resultInterlaced);
                        saveBitmapToGallery(resultInterlaced);
                        onDone.onDone();
                    });

                });
            // Tried delay * 500 --> too short, try static 1500ms
            }, 2 * 850);
        });


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
    /*
    TODO

    FOUNDATION
    [x] Load image into Bitmap
    [x] Extract RGB pixel values
    [x] Create RGB vectors
    [x] Implement Euclidean distance function
    [x] Redesign UI

    K-MEANS
    [x] Randomly initialize centroids
    [x] Assign pixels to nearest centroid
    [x] Group pixels into clusters
    [x] Average cluster members
    [x] Move centroids
    [~] Repeat until convergence
    [~] Create reusable KMeans class

    VISUALIZATION
    [] Visualize dominant colors
    [] Display cluster centroids
    [] Reconstruct clustered image
    [] Add before/after image comparison

    IMAGE PROCESSING
    [x] Move grayscale filter into ImageProcessor
    [] Add image resizing helper?
    [] Add bitmap copy utilities?
    [] Add RGB normalization helper

    SONY CAMERA
    [x] Connect to Sony camera API
    [x] Create SonyCameraClient
    [x] Download captured image as Bitmap
    [] Handle camera disconnects
    [x] Add loading/error states
    [x] Save captured images locally

    ANDROID ARCHITECTURE
    [x] Move networking out of MainActivity
    [x] Move image processing out of MainActivity
    [x] Create ImageProcessor class
    [] Create RGBPixel model
    [] Create Cluster model
    [x] Reduce MainActivity to UI-only logic

    PERFORMANCE
    [] try to get around getPixel() bottleneck?
    [x] Move image processing off UI thread
    [] Add downsampling for large images?

    FUTURE
    [x] Add pixelizer to images
    [] Try to create pixel art effect with conv net
    */











}