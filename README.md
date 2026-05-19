# Lost & Found Map App

An Android app that lets people post lost and found items, browse them on a map, and filter by proximity — built for **SIT708 Task 9.1** at Deakin University.

This is an extension of Task 7.1. The original app added SQLite storage, category filtering, image upload and timestamps. This version adds Google Maps, Places autocomplete, GPS location, and a radius-based search.

---

## What the app does

- **Create an advert** — post a lost or found item with a name, description, category, date, image and location
- **Pick a location two ways** — either type in the location field and choose from Places autocomplete suggestions, or tap "Get Current Location" to fill it from GPS
- **Browse the list** — search and filter all adverts by category or free text
- **View on map** — all adverts with a saved location appear as pins (red = Lost, green = Found)
- **Radius filter** — drag the slider on the map screen to show only items within X km of where you are right now
- **Remove an advert** — tap a pin, open the detail screen, hit Remove once the item is back with its owner

---

## Tech stack

| Thing | What I used |
|---|---|
| Language | Java |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 |
| Local storage | SQLite via `SQLiteOpenHelper` |
| Map | Google Maps SDK for Android 18.2 |
| Location | Fused Location Provider (play-services-location 21.2) |
| Place search | Google Places SDK 3.4 |
| List | RecyclerView + CardView |
| Image picking | `ActivityResultContracts.GetContent` |

---

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/deakin/lostandfound/
│   ├── MainActivity.java           Home — three buttons
│   ├── CreateAdvertActivity.java   Form with Places autocomplete + GPS
│   ├── ListItemsActivity.java      List with search + category filter
│   ├── ItemDetailActivity.java     Detail view + Remove button
│   ├── MapActivity.java            Google Map + radius SeekBar
│   ├── ItemAdapter.java            RecyclerView adapter
│   ├── DatabaseHelper.java         SQLite wrapper (v3 schema)
│   └── Item.java                   Model class
└── res/
    ├── layout/                     5 activity layouts
    ├── values/                     strings, colors, themes
    └── drawable/                   icons and placeholder
```

---

## Getting a Google Maps API key

The app needs a key from Google Cloud before it will run. It takes about 5 minutes.

**Step 1 — Create a Google Cloud project**

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Click the project dropdown → **New Project** → name it anything → **Create**

**Step 2 — Enable the three APIs**

In the left sidebar: **APIs & Services → Library**. Search for and enable each of these:

- Maps SDK for Android
- Places API
- Geocoding API

**Step 3 — Create a key**

**APIs & Services → Credentials → + Create Credentials → API Key**

Copy the key. It starts with `AIzaSy...`

---

## Adding the key to the project

Open `app/src/main/res/values/strings.xml` and find this line:

```xml
<string name="google_maps_key" translatable="false">YOUR_GOOGLE_MAPS_API_KEY_HERE</string>
```

Replace `YOUR_GOOGLE_MAPS_API_KEY_HERE` with your actual key:

```xml
<string name="google_maps_key" translatable="false">AIzaSyXXXXXXXXXXXXXXXXX</string>
```

> **Important:** Never commit your real API key to GitHub. Before pushing, replace it with the placeholder again. If you accidentally push a real key, revoke it immediately in Google Cloud Console and generate a new one.

---

## Building and running

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- On Mac M1/M2/M3: download the **Apple Silicon** version of Android Studio from [developer.android.com/studio](https://developer.android.com/studio)

### Emulator setup (important)

The Maps and Places features need Google Play Services. A plain AOSP emulator won't work.

When creating an AVD:
1. **Tools → Device Manager → + → Create Virtual Device**
2. Pick Pixel 6 → Next
3. On the system image screen, go to **Other Images** tab
4. Choose an image that is **arm64-v8a** + **Google Play** (API 33 or 34)
5. On Mac M1, make sure it's `arm64-v8a` — not `x86_64`

### Run it

1. Open the project in Android Studio
2. Wait for Gradle sync to finish
3. Select your emulator from the device dropdown
4. Hit the green ▶ Run button

---

## How the key features work

### Places autocomplete

The location field in the create-advert screen is set to non-focusable. Tapping it fires an `Autocomplete.IntentBuilder` intent from the Places SDK. The result comes back via `ActivityResultContracts.StartActivityForResult`. The selected place's name fills the field and its `LatLng` is saved alongside the advert.

### GPS current location

The "Get Current Location" button calls `FusedLocationProviderClient.getLastLocation()`. If the permission hasn't been granted yet, it requests it first. Once a location comes back, `Geocoder.getFromLocation()` turns the coordinates into a readable address for the field. The raw lat/lng is still stored in the database.

### Map markers

`MapActivity` calls `DatabaseHelper.getItemsWithLocation()` which skips any rows where both lat and lng are 0 (items created before the geo update, or posted without a location). Lost items get a red marker, Found items get green.

### Radius filter

The SeekBar goes from 0 to 50 km. When the user taps Apply, the activity calls `DatabaseHelper.distanceKm()` — a Haversine implementation — for each item and keeps only those within the chosen distance. A blue circle overlay is drawn at the same radius so you can see the boundary on the map.

### Database migration

The database is at version 3. The `onUpgrade` method handles migrations from any earlier version without dropping existing data:
- v1 → v2 added `category`, `image_path`, `timestamp`
- v2 → v3 added `latitude`, `longitude`

---

## Known limitations

- The app has no user accounts, so anyone on the device can delete any post
- Images are stored in the app's private files directory — they're gone if the app is uninstalled
- The Places autocomplete is in overlay mode (full screen). An embedded fragment would be smoother but requires more setup
