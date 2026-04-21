package com.vypeensoft.friendtracker;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.*;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;


import com.vypeensoft.friendtracker.network.MatrixClient;
import com.vypeensoft.friendtracker.service.LocationService;

import org.json.*;
import com.vypeensoft.friendtracker.util.SettingsPersistenceManager;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private SymbolManager symbolManager;

    private Symbol myLocationSymbol;
    private Map<String, Symbol> friendSymbols = new HashMap<>();

    private DrawerLayout drawer;

    private MatrixClient matrixClient;
    private Handler updateHandler = new Handler();
    private Runnable updateRunnable;
    private long pollingPeriodMs = 10000L;

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

        MapLibre.getInstance(this);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.menu_help, R.string.menu_map_settings);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(map -> {
            this.mapLibreMap = map;

            SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
            String styleUrl = prefs.getString(MapSettingsActivity.KEY_STYLE_URL,
                    "https://tiles.openfreemap.org/styles/liberty");

            map.setStyle(new Style.Builder().fromUri(styleUrl), style -> {

                // ✅ Correct initialization (v9)
                symbolManager = new SymbolManager(mapView, map, style);
                symbolManager.setIconAllowOverlap(true);
                symbolManager.setTextAllowOverlap(true);

                addStyleImage(style, "me-icon", R.drawable.me_marker);
                addStyleImage(style, "friend-icon", R.drawable.friend_marker);
            });
        });

        matrixClient = new MatrixClient(this);

        checkPermissionsAndStartService();
        setupFriendUpdateLoop();
    }

    private void addStyleImage(Style style, String id, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(this, drawableId);
        if (drawable == null) return;

        Bitmap bitmap = bitmapFromDrawable(drawable);
        style.addImage(id, bitmap);
    }

    private Bitmap bitmapFromDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 64;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 64;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void checkPermissionsAndStartService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationService();
        }
    }

    private void startLocationService() {
        Intent intent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void updateMyLocationMarker(double lat, double lon) {
        if (symbolManager == null) return;

        LatLng latLng = new LatLng(lat, lon);

        if (myLocationSymbol == null) {
            SymbolOptions options = new SymbolOptions()
                    .withLatLng(latLng)
                    .withIconImage("me-icon");

            myLocationSymbol = symbolManager.create(options);

            mapLibreMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 14.0)
            );
        } else {
            myLocationSymbol.setLatLng(latLng);
            symbolManager.update(myLocationSymbol);
        }
    }

    private void updateFriendMarker(String userId, double lat, double lon) {
        if (symbolManager == null) return;

        LatLng latLng = new LatLng(lat, lon);

        if (friendSymbols.containsKey(userId)) {
            Symbol symbol = friendSymbols.get(userId);
            symbol.setLatLng(latLng);
            symbolManager.update(symbol);
        } else {
            SymbolOptions options = new SymbolOptions()
                    .withLatLng(latLng)
                    .withIconImage("friend-icon")
                    .withTextField(userId);

            Symbol symbol = symbolManager.create(options);
            friendSymbols.put(userId, symbol);
        }
    }

    private void setupFriendUpdateLoop() {
        updateRunnable = () -> {
            fetchFriendLocations();
            updateHandler.postDelayed(updateRunnable, pollingPeriodMs);
        };
        updateHandler.post(updateRunnable);
    }

    private void fetchFriendLocations() {
        matrixClient.fetchMessages(rawJson ->
                runOnUiThread(() -> parseAndShowFriends(rawJson)));
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

                String type = content.optString("type");
                String body = content.optString("body");

                if (body != null && body.contains("|")) {
                    String[] parts = body.split("\\|");
                    if (parts.length >= 3) {
                        try {
                            String senderId = parts[0];
                            String myDisplayName = matrixClient.getDisplayName();
                            String myDefaultId = "user_" + android.os.Build.ID;
                            String myEffectiveId = (myDisplayName != null && !myDisplayName.isEmpty())
                                    ? myDisplayName : myDefaultId;

                            if (senderId.equals(myEffectiveId)) {
                                continue;
                            }

                            updateFriendMarker(
                                    senderId,
                                    Double.parseDouble(parts[1]),
                                    Double.parseDouble(parts[2])
                            );
                            continue;
                        } catch (Exception ignored) {}
                    }
                }

                if ("location".equals(type)) {
                    updateFriendMarker(
                            content.getString("userId"),
                            content.getDouble("latitude"),
                            content.getDouble("longitude")
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_map_settings) {
            startActivity(new Intent(this, MapSettingsActivity.class));
        } else if (id == R.id.nav_matrix_credentials) {
            startActivity(new Intent(this, MatrixCredentialsActivity.class));
        } else if (id == R.id.nav_groups_rooms) {
            startActivity(new Intent(this, GroupsRoomsActivity.class));
        } else if (id == R.id.nav_help) {
            startActivity(new Intent(this, HelpActivity.class));
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        
        if (SettingsPersistenceManager.hasStoragePermission(this)) {
            SettingsPersistenceManager.importSettings(this);
        } else {
            SettingsPersistenceManager.requestStoragePermission(this);
        }

        matrixClient.loadConfig(this);
        
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        pollingPeriodMs = prefs.getLong(MapSettingsActivity.KEY_MATRIX_POLLING_PERIOD, 10000L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver,
                    new IntentFilter("com.vypeensoft.friendtracker.LOCATION_UPDATE"),
                    Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(locationReceiver,
                    new IntentFilter("com.vypeensoft.friendtracker.LOCATION_UPDATE"));
        }
    }

    @Override protected void onPause() { super.onPause(); mapView.onPause(); unregisterReceiver(locationReceiver); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); updateHandler.removeCallbacks(updateRunnable); }
    @Override public    void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                results.length > 0 &&
                results[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationService();
        } else {
            Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show();
        }
    }
}
