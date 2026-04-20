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
    private String username;
    private String password;
    private String roomAlias;
    
    private final OkHttpClient client;
    private final Gson gson;
    private final Context context;
    private boolean isConnecting = false;

    public MatrixClient(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient();
        this.gson = new Gson();
        loadConfig(this.context);
    }

    public void loadConfig(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.homeserverUrl = prefs.getString(SettingsActivity.KEY_MATRIX_HOMESERVER, "https://matrix-client.matrix.org");
        this.accessToken = prefs.getString(SettingsActivity.KEY_MATRIX_TOKEN, "");
        this.roomId = prefs.getString(SettingsActivity.KEY_MATRIX_ROOM_ID, "");
        this.username = prefs.getString(SettingsActivity.KEY_MATRIX_USERNAME, "");
        this.password = prefs.getString(SettingsActivity.KEY_MATRIX_PASSWORD, "");
        this.roomAlias = prefs.getString(SettingsActivity.KEY_MATRIX_ROOM_ALIAS, "");
        Log.d(TAG, "Config loaded: " + homeserverUrl + " room: " + roomId + " alias: " + roomAlias);
    }

    public boolean isConfigured() {
        return (accessToken != null && !accessToken.isEmpty() && roomId != null && !roomId.isEmpty()) ||
               (username != null && !username.isEmpty() && password != null && !password.isEmpty() && roomAlias != null && !roomAlias.isEmpty());
    }

    private void ensureReady(final Runnable onReady) {
        if (accessToken != null && !accessToken.isEmpty() && roomId != null && !roomId.isEmpty()) {
            onReady.run();
            return;
        }

        if (isConnecting) {
            Log.d(TAG, "Already connecting, skipping ensureReady triggers.");
            return;
        }

        if (username.isEmpty() || password.isEmpty() || roomAlias.isEmpty()) {
            Log.w(TAG, "Insufficient credentials for lazy login");
            return;
        }

        isConnecting = true;
        performLoginAndResolution(onReady);
    }

    private void performLoginAndResolution(final Runnable onReady) {
        if (accessToken == null || accessToken.isEmpty()) {
            login((token) -> {
                this.accessToken = token;
                saveToPrefs(SettingsActivity.KEY_MATRIX_TOKEN, token);
                performLoginAndResolution(onReady); // Proceed to resolve alias or finish
            });
            return;
        }

        if (roomId == null || roomId.isEmpty()) {
            resolveAlias((id) -> {
                this.roomId = id;
                saveToPrefs(SettingsActivity.KEY_MATRIX_ROOM_ID, id);
                isConnecting = false;
                onReady.run();
            });
            return;
        }

        isConnecting = false;
        onReady.run();
    }

    private void login(final java.util.function.Consumer<String> callback) {
        Log.d(TAG, "Attempting lazy login for user: " + username);
        String url = homeserverUrl + "/_matrix/client/r0/login";
        
        java.util.Map<String, Object> bodyMap = new java.util.HashMap<>();
        bodyMap.put("type", "m.login.password");
        bodyMap.put("user", username);
        bodyMap.put("password", password);
        
        String json = gson.toJson(bodyMap);
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder().url(url).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Login failed: " + e.getMessage());
                isConnecting = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    java.util.Map<String, Object> resp = gson.fromJson(response.body().string(), java.util.Map.class);
                    callback.accept((String) resp.get("access_token"));
                } else {
                    Log.e(TAG, "Login response failed: " + response.code());
                    isConnecting = false;
                }
                response.close();
            }
        });
    }

    private void resolveAlias(final java.util.function.Consumer<String> callback) {
        Log.d(TAG, "Resolving room alias: " + roomAlias);
        // Matrix alias resolution: GET /_matrix/client/r0/directory/room/{roomAlias}
        // Need to URL encode the alias
        String encodedAlias = android.net.Uri.encode(roomAlias);
        String url = homeserverUrl + "/_matrix/client/r0/directory/room/" + encodedAlias;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Alias resolution failed: " + e.getMessage());
                isConnecting = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    java.util.Map<String, Object> resp = gson.fromJson(response.body().string(), java.util.Map.class);
                    callback.accept((String) resp.get("room_id"));
                } else {
                    Log.e(TAG, "Alias resolution response failed: " + response.code());
                    isConnecting = false;
                }
                response.close();
            }
        });
    }

    private void saveToPrefs(String key, String value) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(key, value).apply();
    }

    public void sendLocation(LocationMessage message) {
        ensureReady(() -> {
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
        });
    }

    public void fetchMessages(final MatrixListener listener) {
        ensureReady(() -> {
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
        });
    }

    public interface MatrixListener {
        void onNewMessagesReceived(String rawJson);
    }
}
