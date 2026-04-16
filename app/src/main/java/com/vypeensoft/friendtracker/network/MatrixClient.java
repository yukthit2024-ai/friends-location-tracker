package com.vypeensoft.friendtracker.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.vypeensoft.friendtracker.SettingsActivity;
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
    
    private String homeserverUrl;
    private String accessToken;
    private String roomId;
    
    private final OkHttpClient client;
    private final Gson gson;

    public MatrixClient(Context context) {
        this.client = new OkHttpClient();
        this.gson = new Gson();
        loadConfig(context);
    }

    public void loadConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.homeserverUrl = prefs.getString(SettingsActivity.KEY_MATRIX_HOMESERVER, "https://matrix-client.matrix.org");
        this.accessToken = prefs.getString(SettingsActivity.KEY_MATRIX_TOKEN, "");
        this.roomId = prefs.getString(SettingsActivity.KEY_MATRIX_ROOM_ID, "");
        Log.d(TAG, "Config loaded: " + homeserverUrl + " room: " + roomId);
    }

    public boolean isConfigured() {
        return accessToken != null && !accessToken.isEmpty() && roomId != null && !roomId.isEmpty();
    }

    public void sendLocation(LocationMessage message) {
        if (!isConfigured()) {
            Log.w(TAG, "Matrix client not configured. Skipping send.");
            return;
        }

        String url = homeserverUrl + "/_matrix/client/r0/rooms/" + roomId + "/send/m.room.message";
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
                    Log.e(TAG, "Server error on send: " + response.code());
                } else {
                    Log.d(TAG, "Location sent successfully");
                }
                response.close();
            }
        });
    }

    public void fetchMessages(final MatrixListener listener) {
        if (!isConfigured()) {
            return;
        }

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
                    listener.onNewMessagesReceived(response.body().string());
                }
                response.close();
            }
        });
    }

    public interface MatrixListener {
        void onNewMessagesReceived(String rawJson);
    }
}
