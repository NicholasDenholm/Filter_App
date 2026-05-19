package com.example.sony_camera_link_test;

import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
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
        sendCameraRequest(listener);
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

                try {

                    String responseText = response.body().string();
                    JSONObject obj = new JSONObject(responseText);

                    if (obj.has("error")) {
                        listener.onError(new Exception("Camera error"));
                        processNext(); // continue queue
                        return;
                    }

                    JSONArray result = obj.getJSONArray("result");
                    JSONArray urls = result.getJSONArray(0);
                    String imageUrl = urls.getString(0);

                    listener.onSuccess(imageUrl);

                } catch (Exception e) {
                    listener.onError(e);
                }

                processNext(); // ALWAYS continue queue
            }
        });
    }


}
