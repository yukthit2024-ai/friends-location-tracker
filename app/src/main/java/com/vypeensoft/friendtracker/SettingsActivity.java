package com.vypeensoft.friendtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "AppConfig";
    public static final String KEY_MAPBOX_TOKEN = "mapbox_token";
    public static final String KEY_MATRIX_HOMESERVER = "matrix_homeserver";
    public static final String KEY_MATRIX_TOKEN = "matrix_token";
    public static final String KEY_MATRIX_ROOM_ID = "matrix_room_id";

    private TextInputEditText editMapboxToken, editHomeserver, editToken, editRoomId;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editMapboxToken = findViewById(R.id.edit_mapbox_token);
        editHomeserver = findViewById(R.id.edit_matrix_homeserver);
        editToken = findViewById(R.id.edit_matrix_token);
        editRoomId = findViewById(R.id.edit_matrix_room_id);
        btnSave = findViewById(R.id.btn_save);

        loadConfig();

        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editMapboxToken.setText(prefs.getString(KEY_MAPBOX_TOKEN, ""));
        editHomeserver.setText(prefs.getString(KEY_MATRIX_HOMESERVER, "https://matrix-client.matrix.org"));
        editToken.setText(prefs.getString(KEY_MATRIX_TOKEN, ""));
        editRoomId.setText(prefs.getString(KEY_MATRIX_ROOM_ID, ""));
    }

    private void saveConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_MAPBOX_TOKEN, editMapboxToken.getText().toString().trim());
        editor.putString(KEY_MATRIX_HOMESERVER, editHomeserver.getText().toString().trim());
        editor.putString(KEY_MATRIX_TOKEN, editToken.getText().toString().trim());
        editor.putString(KEY_MATRIX_ROOM_ID, editRoomId.getText().toString().trim());

        if (editor.commit()) {
            Toast.makeText(this, "Configuration Saved", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to Save Configuration", Toast.LENGTH_SHORT).show();
        }
    }
}
