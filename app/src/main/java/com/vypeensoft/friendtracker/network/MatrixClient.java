package com.vypeensoft.friendtracker.network;

import android.util.Log;
import com.google.gson.Gson;
import com.vypeensoft.friendtracker.model.LocationMessage;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MatrixClient {
    private static final String TAG = "MatrixClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private String homeserverUrl = "https://matrix-client.matrix.org"; // Default
    private String accessToken = "YOUR_ACCESS_TOKEN";
    private String roomId = "!yourRoomId:matrix.org";
    
    private final OkHttpClient client;
    private final Gson gson;

    public MatrixClient() {
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    public void setConfig(String homeserverUrl, String accessToken, String roomId) {
        this.homeserverUrl = homeserverUrl;
        this.accessToken = accessToken;
        this.roomId = roomId;
    }

    public void sendLocation(LocationMessage message) {
        String url = homeserverUrl + "/_matrix/client/r0/rooms/" + roomId + "/send/m.room.message";
        
        // Wrap our custom location message in a Matrix content structure
        // If the user specifically wants the raw JSON provided in the prompt, we use that.
        String json = gson.toJson(message);
        
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send location: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Server error on send: " + response.code() + " " + response.body().string());
                } else {
                    Log.d(TAG, "Location sent successfully");
                }
                response.close();
            }
        });
    }

    public void fetchMessages(final MatrixListener listener) {
        // Using /messages for simplicity in a "polling" style tracking
        String url = homeserverUrl + "/_matrix/client/r0/rooms/" + roomId + "/messages?limit=10&dir=b";
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to fetch messages: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    // Process messages here... 
                    // Note: This is a placeholder for actual parsing logic
                    listener.onNewMessagesReceived(body);
                } else {
                    Log.e(TAG, "Server error on fetch: " + response.code());
                }
                response.close();
            }
        });
    }

    public interface MatrixListener {
        void onNewMessagesReceived(String rawJson);
    }
}
