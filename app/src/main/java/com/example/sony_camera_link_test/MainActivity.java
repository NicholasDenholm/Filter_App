package com.example.sony_camera_link_test;

import static com.example.sony_camera_link_test.SonyCameraClient.CAMERA_URL;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;

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

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.opencv.android.OpenCVLoader;
import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {


    private SonyCameraClient cameraClient;

    private Bitmap currentImage;
    private String CurrentImageUrl;
    // Default on startup
    private String selectedFilter = "K-Means";

    interface OnBitmapReady {
        void onReady(Bitmap bitmap);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCV", "Unable to load OpenCV!");
        else
            Log.d("OpenCV", "OpenCV loaded Successfully!");

        cameraClient = new SonyCameraClient();

        // Checks the version so that the proper save function is called
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
        }

        // Came with the default template
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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

        // ------------------- Seek bar
        SeekBar seekBar = findViewById(R.id.seekBar2);

        // ------------------- Take photo Button
        Button takePhotoButton = findViewById(R.id.button_photo);
        takePhotoButton.setOnClickListener(v -> {

            takePhotoAsBitmap(bitmap -> {

                currentImage = bitmap;

                ImageView imageView = findViewById(R.id.image_view);
                imageView.setImageBitmap(bitmap);
            });
        });

        // ------------------- Process Photo Button
        Button processPhotoButton = findViewById(R.id.button_process);
        // dynamic filter application
        processPhotoButton.setOnClickListener(v -> {
            int seekBarValue = seekBar.getProgress();
            applyFilterOfChoice(selectedFilter, seekBarValue);
        });

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

    private void takePhotoAsBitmap(OnBitmapReady callback) {
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

    private void setLoading(boolean loading) {

        Button processButton = findViewById(R.id.button_process);
        ProgressBar progressBar = findViewById(R.id.progressBar);

        if (loading) {

            processButton.setEnabled(false);
            processButton.setText("Processing...");

            progressBar.setVisibility(View.VISIBLE);

        } else {

            processButton.setEnabled(true);
            processButton.setText("Process Photo");

            progressBar.setVisibility(View.GONE);
        }
    }

    // ------------------- Saving Images -------------------
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
    private void applyFilterOfChoice(String filter, int k) {
        setLoading(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {

            //Bitmap result = null;

            if (filter.equals("K-Means")) {
                Log.d("SEEK BAR", "k in k-means is " + k);
                applyKMeansThreaded(k);
            }

            else if (filter.equals("Pixelate")) {
                Log.d("SEEK BAR", "pixelation strength is " + k);
                applyPixelated(k);
            }

            else if (filter.equals("Grayscale")) {
                applyGrayScale();
            }
            else if (filter.equals("Interlaced")) {
                captureInterlaced(k);
            }


            // Each method handles the UI filtered image display
            // ALWAYS return to UI thread at the end
            runOnUiThread(() -> setLoading(false));
        });
        // Wrong place: Animators may only be run on Looper threads
        setLoading(false);
    }

    private void applyGrayScale() {
        if (currentImage == null) {
            Log.e("APPLY FILTER", "No image provided");
            return;
        }
        Bitmap greyScaleImg = ImageProcessor.toGrayScale(currentImage);

        runOnUiThread(() -> {
            setCurrentImage(greyScaleImg);
            saveBitmapToGallery(greyScaleImg);
        });
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

    private void applyKMeansThreaded(int k_for_kmeans) {

        if (currentImage == null) {
            Log.e("APPLY KMEANS", "No image provided");
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

    private void applyPixelated(int pixelationStrength) {

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

    private void captureInterlaced(int delay) {
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
                    });

                });
            // Tried delay * 500 --> too short, try static 1500ms
            }, 2 * 750);
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
    [] Add image resizing helper
    [] Add bitmap copy utilities
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
    [] Add downsampling for large images

    FUTURE
    [x] Add pixelizer to images
    [] Try to create pixel art effect with conv net
    */











}