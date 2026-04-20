package com.vypeensoft.friendtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class MatrixCredentialsActivity extends AppCompatActivity {

    private TextInputEditText editHomeserver, editUsername, editPassword;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix_credentials);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editHomeserver = findViewById(R.id.edit_matrix_homeserver);
        editUsername = findViewById(R.id.edit_matrix_username);
        editPassword = findViewById(R.id.edit_matrix_password);
        btnSave = findViewById(R.id.btn_save);

        loadConfig();

        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        editHomeserver.setText(prefs.getString(MapSettingsActivity.KEY_MATRIX_HOMESERVER, "https://matrix-client.matrix.org"));
        editUsername.setText(prefs.getString(MapSettingsActivity.KEY_MATRIX_USERNAME, ""));
        editPassword.setText(prefs.getString(MapSettingsActivity.KEY_MATRIX_PASSWORD, ""));
    }

    private void saveConfig() {
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(MapSettingsActivity.KEY_MATRIX_HOMESERVER, editHomeserver.getText().toString().trim());
        editor.putString(MapSettingsActivity.KEY_MATRIX_USERNAME, editUsername.getText().toString().trim());
        editor.putString(MapSettingsActivity.KEY_MATRIX_PASSWORD, editPassword.getText().toString().trim());

        // Clear cached token when credentials change
        editor.remove(MapSettingsActivity.KEY_MATRIX_TOKEN);

        if (editor.commit()) {
            Toast.makeText(this, "Credentials Saved", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to Save Credentials", Toast.LENGTH_SHORT).show();
        }
    }
}
