package com.deakin.lostandfound;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Create-advert form.
 *
 * New in 9.1:
 *   - Location field opens Places Autocomplete when tapped (subtask requirement)
 *   - "GET CURRENT LOCATION" button fills the field from the device GPS
 *   - Lat/lng is stored with the advert so it can appear on the map
 */
public class CreateAdvertActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_CODE = 1001;

    private RadioGroup rgPostType;
    private EditText etName, etPhone, etDescription, etDate, etLocation;
    private Spinner spinnerCategory;
    private ImageView imgPreview;
    private Button btnPickImage, btnGetLocation, btnSave;

    private Uri pickedImageUri   = null;
    private String savedImagePath = null;

    // Coordinates filled by either the autocomplete or the GPS button.
    private double pickedLat = 0.0;
    private double pickedLng = 0.0;

    private FusedLocationProviderClient fusedLocationClient;

    // -----------------------------------------------------------------------
    //  Activity-result launchers
    // -----------------------------------------------------------------------

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    new ActivityResultCallback<Uri>() {
                        @Override
                        public void onActivityResult(Uri uri) {
                            if (uri != null) {
                                pickedImageUri = uri;
                                imgPreview.setImageURI(uri);
                                imgPreview.setVisibility(View.VISIBLE);
                            }
                        }
                    });

    // Places autocomplete returns an Intent result
    private final ActivityResultLauncher<Intent> autocompleteLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                                Place place = Autocomplete.getPlaceFromIntent(result.getData());
                                etLocation.setText(place.getName());
                                if (place.getLatLng() != null) {
                                    pickedLat = place.getLatLng().latitude;
                                    pickedLng = place.getLatLng().longitude;
                                }
                            } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR
                                    && result.getData() != null) {
                                com.google.android.gms.common.api.Status status =
                                        Autocomplete.getStatusFromIntent(result.getData());
                                Toast.makeText(CreateAdvertActivity.this,
                                        "Autocomplete error: " + status.getStatusMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_advert);

        // Initialise Places SDK (safe to call repeatedly — it no-ops if already initialised)
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.create_new_advert);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rgPostType      = findViewById(R.id.rgPostType);
        etName          = findViewById(R.id.etName);
        etPhone         = findViewById(R.id.etPhone);
        etDescription   = findViewById(R.id.etDescription);
        etDate          = findViewById(R.id.etDate);
        etLocation      = findViewById(R.id.etLocation);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        imgPreview      = findViewById(R.id.imgPreview);
        btnPickImage    = findViewById(R.id.btnPickImage);
        btnGetLocation  = findViewById(R.id.btnGetLocation);
        btnSave         = findViewById(R.id.btnSave);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        // Tap the date field → DatePickerDialog
        etDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showDatePicker(); }
        });

        // Tap the location field → Places Autocomplete overlay
        etLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { openAutocomplete(); }
        });
        etLocation.setFocusable(false);  // prevent keyboard appearing, force picker

        btnPickImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { imagePickerLauncher.launch("image/*"); }
        });

        btnGetLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { fetchCurrentLocation(); }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { attemptSave(); }
        });
    }

    // -----------------------------------------------------------------------
    //  Places autocomplete
    // -----------------------------------------------------------------------

    private void openAutocomplete() {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .build(this);
        autocompleteLauncher.launch(intent);
    }

    // -----------------------------------------------------------------------
    //  GPS — GET CURRENT LOCATION
    // -----------------------------------------------------------------------

    private void fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_CODE);
            return;
        }
        doGetLastLocation();
    }

    private void doGetLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(
                new OnSuccessListener<android.location.Location>() {
                    @Override
                    public void onSuccess(android.location.Location location) {
                        if (location == null) {
                            Toast.makeText(CreateAdvertActivity.this,
                                    "Couldn't get location. Make sure GPS is on.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        pickedLat = location.getLatitude();
                        pickedLng = location.getLongitude();
                        reverseGeocode(pickedLat, pickedLng);
                    }
                });
    }

    /**
     * Turns a lat/lng pair into a human-readable address and stuffs it into
     * the location field. Falls back to "lat, lng" if the Geocoder fails.
     */
    private void reverseGeocode(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(addr.getAddressLine(i));
                }
                etLocation.setText(sb.toString());
            } else {
                etLocation.setText(String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng));
            }
        } catch (IOException e) {
            e.printStackTrace();
            etLocation.setText(String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doGetLastLocation();
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------------------------------
    //  DatePicker
    // -----------------------------------------------------------------------

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, year, month, day) -> {
                    etDate.setText(String.format(Locale.getDefault(),
                            "%02d/%02d/%04d", day, month + 1, year));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // -----------------------------------------------------------------------
    //  Save
    // -----------------------------------------------------------------------

    private void attemptSave() {
        String postType    = rgPostType.getCheckedRadioButtonId() == R.id.rbFound ? "Found" : "Lost";
        String name        = etName.getText().toString().trim();
        String phone       = etPhone.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String date        = etDate.getText().toString().trim();
        String location    = etLocation.getText().toString().trim();
        String category    = spinnerCategory.getSelectedItem().toString();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter a name for the item"); etName.requestFocus(); return;
        }
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone number required"); etPhone.requestFocus(); return;
        }
        if (TextUtils.isEmpty(description)) {
            etDescription.setError("Add a description"); etDescription.requestFocus(); return;
        }
        if (TextUtils.isEmpty(date)) {
            Toast.makeText(this, "Pick a date", Toast.LENGTH_SHORT).show(); return;
        }
        if (TextUtils.isEmpty(location)) {
            Toast.makeText(this, "Add a location — tap the field or use the button",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (pickedImageUri == null) {
            Toast.makeText(this, "An image is required for every post",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        savedImagePath = copyImageToInternalStorage(pickedImageUri);
        if (savedImagePath == null) {
            Toast.makeText(this, "Couldn't save the image, try again",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Item item = new Item(0, postType, name, phone, description, date,
                location, category, savedImagePath,
                System.currentTimeMillis(), pickedLat, pickedLng);

        DatabaseHelper db = new DatabaseHelper(this);
        long newId = db.insertItem(item);

        if (newId != -1) {
            Toast.makeText(this, "Advert saved", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, ListItemsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        } else {
            Toast.makeText(this, "Something went wrong while saving",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String copyImageToInternalStorage(Uri sourceUri) {
        try {
            InputStream in = getContentResolver().openInputStream(sourceUri);
            if (in == null) return null;
            File dir = new File(getFilesDir(), "advert_images");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File outFile = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
            out.flush(); out.close(); in.close();
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
