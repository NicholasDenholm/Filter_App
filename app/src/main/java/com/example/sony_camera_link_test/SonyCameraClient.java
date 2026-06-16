package com.example.sony_camera_link_test;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;

import okhttp3.Callback;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SonyCameraClient {
    public static final String CAMERA_URL =
            "http://192.168.122.1:8080/sony/camera";

    private final OkHttpClient client = new OkHttpClient();

    // Lock which is important for interpolate --> trying queue approach
    //private boolean isCameraBusy = false;

    private boolean isProcessing = false;
    // Trying queue system
    private final Queue<OnPictureTakenListener> queue = new LinkedList<>();

    class CameraRequest {
        OnPictureTakenListener listener;
        long timestamp;
    }

    public interface OnPictureTakenListener {
        void onSuccess(String imageUrl);
        void onError(Exception e);
    }

    public void takePicture(OnPictureTakenListener listener) {
        queue.add(listener);

        if (!isProcessing) {
            processNext();
        }
    }

    private void processNext() {
        if (queue.isEmpty()) {
            isProcessing = false;
            return;
        }

        isProcessing = true;
        OnPictureTakenListener listener = queue.poll();
        //sendCameraRequest(listener);

        // Enforce a 600ms hardware recovery window before hitting the camera again
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            sendCameraRequest(listener);
        }, 600); // Adjust this value if your specific Sony model needs more time to write to the SD card
    }

    private void sendCameraRequest(OnPictureTakenListener listener) {

        String json = "{\"method\":\"actTakePicture\",\"params\":[],\"id\":1,\"version\":\"1.0\"}";

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder().url(CAMERA_URL).post(body).build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                listener.onError(e);
                processNext(); // move to next request
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                if (!response.isSuccessful()) {
                    String errorMsg = "Sony HTTP Error Code: " + response.code();
                    Log.e("SONY_CLIENT_DEBUG", errorMsg);
                    listener.onError(new Exception(errorMsg));
                    processNext(); // Clean up your queue runner
                    return;
                }

                if (response.body() == null) {
                    listener.onError(new Exception("Sony returned an empty response body."));
                    processNext();
                    return;
                }

                // Read the raw JSON string from the camera
                String rawJson = response.body().string();
                Log.d("SONY_CLIENT_DEBUG", "Raw JSON response from camera: " + rawJson);

                // Inspect if the Sony API returned an internal hardware error array
                if (rawJson.contains("\"error\"") || rawJson.contains("errCode")) {
                    Log.e("SONY_CLIENT_DEBUG", "Sony hardware rejected command: " + rawJson);
                    listener.onError(new Exception("Sony API hardware error payload: " + rawJson));
                    processNext();
                    return;
                }

                try {
                    JSONObject jsonObject = new JSONObject(rawJson);

                    if (jsonObject.has("result")) {
                        // 1. Get the outer "result" array -> [ ["http://..."] ]
                        JSONArray resultArray = jsonObject.getJSONArray("result");

                        // 2. Extract the first inner array layer -> ["http://..."]
                        JSONArray innerUrlArray = resultArray.getJSONArray(0);

                        // 3. Grab the actual string link at index 0
                        String extractedImageUrl = innerUrlArray.getString(0);

                        Log.d("SONY_CLIENT_DEBUG", "Successfully parsed image URL: " + extractedImageUrl);

                        // Hand the clean URL over to your download loop
                        listener.onSuccess(extractedImageUrl);

                    } else {
                        Log.e("SONY_CLIENT_DEBUG", "JSON valid but missing 'result' block: " + rawJson);
                        listener.onError(new Exception("Sony API response formatting anomaly."));
                    }
                } catch (JSONException e) {
                    Log.e("SONY_CLIENT_DEBUG", "Parser crashed! Failed to unpack nested JSON array layers.", e);
                    listener.onError(e);
                }
                processNext(); // ALWAYS continue queue
            }
        });
    }

    public interface OnBitmapReadyListener {
        void onSuccess(Bitmap bitmap);
        void onError(Exception e);
    }

    /*
    // 2. Add this method to reuse your internal 'client' instance pool
    public void downloadBitmap(String imageUrl, OnBitmapReadyListener listener) {
        // DEFENSIVE GUARD: Catch null or empty URLs before OkHttp can crash the app
        if (imageUrl == null || imageUrl.isEmpty()) {
            listener.onError(new IllegalArgumentException("Download aborted: Target URL string is null or empty."));
            return;
        }

        Request request = new Request.Builder().url(imageUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                listener.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMsg = "Sony HTTP Error Code: " + response.code();
                    Log.e("SONY_CLIENT_DEBUG", "downloading bitmap: " + errorMsg);
                    listener.onError(new IOException("Sony server returned invalid response code: " + response.code()));
                    processNext(); // Clean up your queue runner
                    return;
                }

                if (response.body() == null) {
                    listener.onError(new Exception("Sony returned an empty response body."));
                    processNext();
                    return;
                }

                try {
                    // Read byte array matrix data
                    byte[] bytes = response.body().bytes();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                    if (bitmap != null) {
                        // 3. Automatically bounce execution to the Main/UI Thread
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                listener.onSuccess(bitmap)
                        );
                    } else {
                        listener.onError(new Exception("BitmapFactory returned null while processing byte stream."));
                    }
                } catch (Exception e) {
                    listener.onError(e);
                }
            }
        });
    }

     */

}
