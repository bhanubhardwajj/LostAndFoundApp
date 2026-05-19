package com.deakin.lostandfound;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows all Lost & Found adverts as markers on a Google Map.
 *
 * Two radius controls at the bottom let the user filter markers to
 * only show items within X km of their current GPS position:
 *   - SeekBar (0-50 km, 0 = "show all")
 *   - A live label showing the current value
 *
 * Tapping a marker opens a small info window with the item name,
 * post type and distance. Tapping the info window navigates to the
 * full ItemDetailActivity.
 */
public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_CODE = 2001;
    private static final int DEFAULT_ZOOM = 12;

    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseHelper db;

    private TextView tvRadiusLabel;
    private SeekBar seekBarRadius;
    private Button btnApplyRadius;

    private android.location.Location myLocation = null;
    private Circle radiusCircle = null;

    // Keeps a map from Marker → Item.id so we can open the detail screen
    private final java.util.HashMap<Marker, Long> markerItemMap = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.show_on_map);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = new DatabaseHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        tvRadiusLabel  = findViewById(R.id.tvRadiusLabel);
        seekBarRadius  = findViewById(R.id.seekBarRadius);
        btnApplyRadius = findViewById(R.id.btnApplyRadius);

        // SeekBar: 0 = no filter, 1-50 = km radius
        seekBarRadius.setMax(50);
        seekBarRadius.setProgress(0);
        tvRadiusLabel.setText(getString(R.string.radius_all));

        seekBarRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 0) {
                    tvRadiusLabel.setText(getString(R.string.radius_all));
                } else {
                    tvRadiusLabel.setText(getString(R.string.radius_km, progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        btnApplyRadius.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int radiusKm = seekBarRadius.getProgress();
                if (radiusKm > 0 && myLocation == null) {
                    Toast.makeText(MapActivity.this,
                            "Getting your location first…", Toast.LENGTH_SHORT).show();
                    fetchLocationThenFilter(radiusKm);
                } else {
                    refreshMarkers(radiusKm);
                }
            }
        });

        // Kick off the map load
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    // -----------------------------------------------------------------------
    //  Map ready callback
    // -----------------------------------------------------------------------

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Try to enable the blue "my location" dot
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }

        // Open detail screen when user taps an info window
        googleMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(@NonNull Marker marker) {
                Long itemId = markerItemMap.get(marker);
                if (itemId != null) {
                    Intent i = new Intent(MapActivity.this, ItemDetailActivity.class);
                    i.putExtra(ItemDetailActivity.EXTRA_ITEM_ID, itemId);
                    startActivity(i);
                }
            }
        });

        // Load all items with location and drop initial markers
        refreshMarkers(0);

        // Fetch location in the background so the "use current" feature
        // is ready if the user slides the radius bar
        fetchMyLocation();
    }

    // -----------------------------------------------------------------------
    //  Marker management
    // -----------------------------------------------------------------------

    /**
     * Clears the map and re-adds markers, optionally filtered to items within
     * {@code radiusKm} km of the user's current position.
     */
    private void refreshMarkers(int radiusKm) {
        if (googleMap == null) return;
        googleMap.clear();
        markerItemMap.clear();

        if (radiusCircle != null) {
            radiusCircle.remove();
            radiusCircle = null;
        }

        List<Item> allItems = db.getItemsWithLocation();
        List<Item> toShow;

        if (radiusKm <= 0 || myLocation == null) {
            // No filter — show everything
            toShow = allItems;
        } else {
            // Filter by radius using the Haversine formula
            toShow = new ArrayList<>();
            for (Item item : allItems) {
                double dist = DatabaseHelper.distanceKm(
                        myLocation.getLatitude(), myLocation.getLongitude(),
                        item.getLatitude(), item.getLongitude());
                if (dist <= radiusKm) toShow.add(item);
            }

            // Draw the radius circle around the user
            LatLng center = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
            radiusCircle = googleMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(radiusKm * 1000.0)   // metres
                    .strokeColor(Color.parseColor("#1976D2"))
                    .strokeWidth(3)
                    .fillColor(Color.parseColor("#221976D2")));

            // Move camera to user
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center,
                    radiusToZoom(radiusKm)));
        }

        if (toShow.isEmpty()) {
            Toast.makeText(this,
                    radiusKm > 0
                            ? "No items found within " + radiusKm + " km"
                            : "No items have a location yet",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        LatLng firstLatLng = null;
        for (Item item : toShow) {
            LatLng pos = new LatLng(item.getLatitude(), item.getLongitude());
            float hue = item.getPostType().equalsIgnoreCase("Lost")
                    ? BitmapDescriptorFactory.HUE_RED
                    : BitmapDescriptorFactory.HUE_GREEN;

            // Distance string in the snippet if we have user location
            String snippet;
            if (myLocation != null) {
                double dist = DatabaseHelper.distanceKm(
                        myLocation.getLatitude(), myLocation.getLongitude(),
                        item.getLatitude(), item.getLongitude());
                snippet = String.format(Locale.getDefault(),
                        "%s · %.1f km away · %s", item.getCategory(), dist, item.getLocation());
            } else {
                snippet = item.getCategory() + " · " + item.getLocation();
            }

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(item.getPostType() + ": " + item.getName())
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));

            if (marker != null) markerItemMap.put(marker, item.getId());
            if (firstLatLng == null) firstLatLng = pos;
        }

        // Pan to the cluster if not already filtering by radius
        if (radiusKm <= 0 && firstLatLng != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstLatLng, DEFAULT_ZOOM));
        }
    }

    // -----------------------------------------------------------------------
    //  Location helpers
    // -----------------------------------------------------------------------

    private void fetchMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_CODE);
            return;
        }
        doFetchLocation();
    }

    private void fetchLocationThenFilter(int radiusKm) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(
                new OnSuccessListener<android.location.Location>() {
                    @Override
                    public void onSuccess(android.location.Location location) {
                        if (location != null) {
                            myLocation = location;
                            refreshMarkers(radiusKm);
                        } else {
                            Toast.makeText(MapActivity.this,
                                    "Couldn't get your location. Is GPS on?",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void doFetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(
                new OnSuccessListener<android.location.Location>() {
                    @Override
                    public void onSuccess(android.location.Location location) {
                        if (location != null) myLocation = location;
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == LOCATION_PERMISSION_CODE
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            doFetchLocation();
            if (googleMap != null && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.setMyLocationEnabled(true);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Utilities
    // -----------------------------------------------------------------------

    /** Rough zoom level that shows a circle of the given radius comfortably. */
    private float radiusToZoom(int radiusKm) {
        if (radiusKm <= 1)  return 15;
        if (radiusKm <= 5)  return 13;
        if (radiusKm <= 10) return 11;
        if (radiusKm <= 25) return 10;
        return 9;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh markers in case the user removed an item on the detail screen
        if (googleMap != null) refreshMarkers(seekBarRadius.getProgress());
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
