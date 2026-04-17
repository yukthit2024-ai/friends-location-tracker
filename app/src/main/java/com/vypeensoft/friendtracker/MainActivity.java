package com.vypeensoft.friendtracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.plugin.annotation.AnnotationConfig;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.AnnotationPluginImplKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;
import com.google.android.material.navigation.NavigationView;
import com.vypeensoft.friendtracker.network.MatrixClient;
import com.vypeensoft.friendtracker.service.LocationService;
import com.mapbox.maps.MapboxOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private PointAnnotationManager pointAnnotationManager;
    private DrawerLayout drawer;
    
    private PointAnnotation myLocationAnnotation;
    private Map<String, PointAnnotation> friendAnnotations = new HashMap<>();
    
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
        
        // Load Mapbox token from settings and set it globally
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String mapboxToken = prefs.getString(SettingsActivity.KEY_MAPBOX_TOKEN, "");
        if (!mapboxToken.isEmpty()) {
            MapboxOptions.setAccessToken(mapboxToken);
        }

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

        mapView = findViewById(R.id.mapView);
        mapboxMap = mapView.getMapboxMap();
        
        // Initialize Annotation Plugin
        AnnotationPlugin annotationApi = AnnotationPluginImplKt.getAnnotations(mapView);
        pointAnnotationManager = PointAnnotationManagerKt.createPointAnnotationManager(annotationApi, new AnnotationConfig());

        mapboxMap.loadStyle(Style.MAPBOX_STREETS, style -> {
            // Add custom icons to the style
            addStyleImage(style, "me-icon", R.drawable.me_marker);
            addStyleImage(style, "friend-icon", R.drawable.friend_marker);
        });

        matrixClient = new MatrixClient(this);
        
        checkPermissionsAndStartService();
        setupFriendUpdateLoop();
    }

    private void addStyleImage(Style style, String id, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(this, drawableId);
        if (drawable != null) {
            Bitmap bitmap = bitmapFromDrawable(drawable);
            style.addImage(id, bitmap);
        }
    }

    private Bitmap bitmapFromDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
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

    private void updateMyLocationMarker(double lat, double lon) {
        Point point = Point.fromLngLat(lon, lat);
        if (myLocationAnnotation == null) {
            PointAnnotationOptions options = new PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage("me-icon");
            myLocationAnnotation = pointAnnotationManager.create(options);
            
            // Initial camera set
            mapboxMap.setCamera(new CameraOptions.Builder()
                    .center(point)
                    .zoom(14.0)
                    .build());
        } else {
            myLocationAnnotation.setPoint(point);
            pointAnnotationManager.update(myLocationAnnotation);
        }
    }

    private void setupFriendUpdateLoop() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                fetchFriendLocations();
                updateHandler.postDelayed(this, 10000);
            }
        };
        updateHandler.post(updateRunnable);
    }

    private void fetchFriendLocations() {
        matrixClient.fetchMessages(rawJson -> runOnUiThread(() -> parseAndShowFriends(rawJson)));
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
        Point point = Point.fromLngLat(lon, lat);
        if (friendAnnotations.containsKey(userId)) {
            PointAnnotation annotation = friendAnnotations.get(userId);
            annotation.setPoint(point);
            pointAnnotationManager.update(annotation);
        } else {
            PointAnnotationOptions options = new PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage("friend-icon")
                    .withTextField(userId); // Show label
            PointAnnotation annotation = pointAnnotationManager.create(options);
            friendAnnotations.put(userId, annotation);
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
