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
    public static final String KEY_STYLE_URL = "map_style_url";
    public static final String KEY_MATRIX_HOMESERVER = "matrix_homeserver";
    public static final String KEY_MATRIX_TOKEN = "matrix_token";
    public static final String KEY_MATRIX_ROOM_ID = "matrix_room_id";
    public static final String KEY_MATRIX_USERNAME = "matrix_username";
    public static final String KEY_MATRIX_PASSWORD = "matrix_password";
    public static final String KEY_MATRIX_ROOM_ALIAS = "matrix_room_alias";

    private TextInputEditText editStyleUrl, editHomeserver, editUsername, editPassword, editRoomAlias;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editStyleUrl = findViewById(R.id.edit_mapbox_token);
        editHomeserver = findViewById(R.id.edit_matrix_homeserver);
        editUsername = findViewById(R.id.edit_matrix_username);
        editPassword = findViewById(R.id.edit_matrix_password);
        editRoomAlias = findViewById(R.id.edit_matrix_room_alias);
        btnSave = findViewById(R.id.btn_save);

        loadConfig();

        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editStyleUrl.setText(prefs.getString(KEY_STYLE_URL, "https://tiles.openfreemap.org/styles/liberty"));
        editHomeserver.setText(prefs.getString(KEY_MATRIX_HOMESERVER, "https://matrix-client.matrix.org"));
        editUsername.setText(prefs.getString(KEY_MATRIX_USERNAME, ""));
        editPassword.setText(prefs.getString(KEY_MATRIX_PASSWORD, ""));
        editRoomAlias.setText(prefs.getString(KEY_MATRIX_ROOM_ALIAS, ""));
    }

    private void saveConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_STYLE_URL, editStyleUrl.getText().toString().trim());
        editor.putString(KEY_MATRIX_HOMESERVER, editHomeserver.getText().toString().trim());
        editor.putString(KEY_MATRIX_USERNAME, editUsername.getText().toString().trim());
        editor.putString(KEY_MATRIX_PASSWORD, editPassword.getText().toString().trim());
        editor.putString(KEY_MATRIX_ROOM_ALIAS, editRoomAlias.getText().toString().trim());

        // Clear cached token and room ID when config changes to trigger re-login/re-resolution
        editor.remove(KEY_MATRIX_TOKEN);
        editor.remove(KEY_MATRIX_ROOM_ID);

        if (editor.commit()) {
            Toast.makeText(this, "Configuration Saved", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to Save Configuration", Toast.LENGTH_SHORT).show();
        }
    }
}
