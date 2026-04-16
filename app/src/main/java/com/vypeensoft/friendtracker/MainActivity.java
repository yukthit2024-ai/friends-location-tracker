package com.vypeensoft.friendtracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.navigation.NavigationView;
import com.vypeensoft.friendtracker.network.MatrixClient;
import com.vypeensoft.friendtracker.service.LocationService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, NavigationView.OnNavigationItemSelectedListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private GoogleMap mMap;
    private DrawerLayout drawer;
    private Marker myLocationMarker;
    private Map<String, Marker> friendMarkers = new HashMap<>();
    
    private MatrixClient matrixClient;
    private Handler updateHandler = new Handler();
    private Runnable updateRunnable;

    private BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double lat = intent.getDoubleExtra("latitude", 0);
            double lon = intent.getDoubleExtra("longitude", 0);
            updateMyLocationMarker(lat, lon);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.menu_help, R.string.menu_settings);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        matrixClient = new MatrixClient(this);
        
        checkPermissionsAndStartService();
        setupFriendUpdateLoop();
    }

    private void checkPermissionsAndStartService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationService();
        }
    }

    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private void updateMyLocationMarker(double lat, double lon) {
        LatLng latLng = new LatLng(lat, lon);
        if (myLocationMarker == null) {
            myLocationMarker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Me")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        } else {
            myLocationMarker.setPosition(latLng);
        }
    }

    private void setupFriendUpdateLoop() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                fetchFriendLocations();
                updateHandler.postDelayed(this, 10000); // Update every 10s
            }
        };
        updateHandler.post(updateRunnable);
    }

    private void fetchFriendLocations() {
        matrixClient.fetchMessages(new MatrixClient.MatrixListener() {
            @Override
            public void onNewMessagesReceived(String rawJson) {
                runOnUiThread(() -> parseAndShowFriends(rawJson));
            }
        });
    }

    private void parseAndShowFriends(String rawJson) {
        try {
            JSONObject obj = new JSONObject(rawJson);
            JSONArray chunk = obj.optJSONArray("chunk");
            if (chunk == null) return;

            for (int i = 0; i < chunk.length(); i++) {
                JSONObject event = chunk.getJSONObject(i);
                JSONObject content = event.optJSONObject("content");
                if (content == null) continue;

                if ("location".equals(content.optString("type"))) {
                    String userId = content.getString("userId");
                    double lat = content.getDouble("latitude");
                    double lon = content.getDouble("longitude");

                    updateFriendMarker(userId, lat, lon);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateFriendMarker(String userId, double lat, double lon) {
        // Skip updating "me" if my userId matches (self-tracking check)
        // For simplicity, we assume friend markers are distinct
        LatLng latLng = new LatLng(lat, lon);
        if (friendMarkers.containsKey(userId)) {
            friendMarkers.get(userId).setPosition(latLng);
        } else {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(userId)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            friendMarkers.put(userId, marker);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.nav_help) {
            startActivity(new Intent(this, HelpActivity.class));
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (matrixClient != null) {
            matrixClient.loadConfig(this);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, new IntentFilter("com.vypeensoft.friendtracker.LOCATION_UPDATE"), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(locationReceiver, new IntentFilter("com.vypeensoft.friendtracker.LOCATION_UPDATE"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(locationReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService();
            } else {
                Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show();
            }
        }
    }
}
