package com.vypeensoft.friendtracker.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.vypeensoft.friendtracker.MapSettingsActivity;
import com.vypeensoft.friendtracker.GroupsRoomsActivity;
import com.vypeensoft.friendtracker.model.GroupRoom;
import com.vypeensoft.friendtracker.model.LocationMessage;
import com.vypeensoft.friendtracker.util.AppLogger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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
    private String displayName;
    
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
        SharedPreferences prefs = context.getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.homeserverUrl = prefs.getString(MapSettingsActivity.KEY_MATRIX_HOMESERVER, "https://matrix-client.matrix.org");
        this.accessToken = prefs.getString(MapSettingsActivity.KEY_MATRIX_TOKEN, "");
        this.username = prefs.getString(MapSettingsActivity.KEY_MATRIX_USERNAME, "");
        this.password = prefs.getString(MapSettingsActivity.KEY_MATRIX_PASSWORD, "");
        this.displayName = prefs.getString(MapSettingsActivity.KEY_MATRIX_DISPLAY_NAME, "");
        
        // Find active room ID from list
        this.roomId = "";
        String roomsJson = prefs.getString(GroupsRoomsActivity.KEY_MATRIX_ROOMS, "");
        if (!roomsJson.isEmpty()) {
            Type type = new TypeToken<ArrayList<GroupRoom>>() {}.getType();
            List<GroupRoom> rooms = gson.fromJson(roomsJson, type);
            for (GroupRoom room : rooms) {
                if (room.isActive()) {
                    this.roomId = room.getRoomId();
                    break;
                }
            }
        }
        
        AppLogger.log(context, TAG, String.format("Config loaded: Homeserver=%s, ActiveRoom=%s, User=%s", 
                homeserverUrl, roomId, username));
    }

    public boolean isConfigured() {
        return (accessToken != null && !accessToken.isEmpty() && roomId != null && !roomId.isEmpty()) ||
               (username != null && !username.isEmpty() && password != null && !password.isEmpty() && roomId != null && !roomId.isEmpty());
    }

    private void ensureReady(final Runnable onReady) {
        if (roomId == null || roomId.isEmpty()) {
            AppLogger.log(context, TAG, "Matrix Message Send Status: SKIPPED (No active room selected)");
            return;
        }

        if (accessToken != null && !accessToken.isEmpty()) {
            onReady.run();
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            AppLogger.log(context, TAG, "Matrix Message Send Status: SKIPPED (Insufficient credentials for lazy login)");
            return;
        }

        isConnecting = true;
        performLogin(onReady);
    }

    private void performLogin(final Runnable onReady) {
        if (accessToken == null || accessToken.isEmpty()) {
            login((token) -> {
                this.accessToken = token;
                saveToPrefs(MapSettingsActivity.KEY_MATRIX_TOKEN, token);
                AppLogger.log(context, TAG, "Matrix Login Status: SUCCESS for user: " + username);
                isConnecting = false;
                onReady.run();
            });
            return;
        }

        isConnecting = false;
        onReady.run();
    }

    private void login(final java.util.function.Consumer<String> callback) {
        AppLogger.log(context, TAG, "Attempting login to " + homeserverUrl + " for user: " + username);
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
                AppLogger.logError(context, TAG, "Matrix Login Status: FAILED with exception", e);
                isConnecting = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    java.util.Map<String, Object> resp = gson.fromJson(response.body().string(), java.util.Map.class);
                    callback.accept((String) resp.get("access_token"));
                } else {
                    AppLogger.log(context, TAG, "Matrix Login Status: FAILED (Code: " + response.code() + ")");
                    isConnecting = false;
                }
                response.close();
            }
        });
    }

    private void saveToPrefs(String key, String value) {
        SharedPreferences prefs = context.getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(key, value).apply();
    }

    public void sendLocation(LocationMessage message) {
        ensureReady(() -> {
            String txnId = "m" + System.currentTimeMillis();
            String url = homeserverUrl + "/_matrix/client/v3/rooms/" + roomId + "/send/m.room.message/" + txnId;
            String json = gson.toJson(message);
            
            AppLogger.log(context, TAG, "Sending Matrix message to Room: " + roomId);
            AppLogger.log(context, TAG, "Message Content: " + json);

            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .put(body)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    AppLogger.logError(context, TAG, "Matrix Message Send Status: FAILED with exception for room: " + roomId, e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        AppLogger.log(context, TAG, "Matrix Message Send Status: FAILED (Code: " + response.code() + ") for room: " + roomId);
                    } else {
                        AppLogger.log(context, TAG, "Matrix Message Send Status: SUCCESS for room: " + roomId);
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

    public String getDisplayName() {
        return displayName;
    }

    public interface MatrixListener {
        void onNewMessagesReceived(String rawJson);
    }
}
