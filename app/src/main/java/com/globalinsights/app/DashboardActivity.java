package com.globalinsights.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.globalinsights.app.data.CountryBroadcastReceiver;
import com.globalinsights.app.data.DatabaseHelper;
import com.globalinsights.app.data.FirebaseLocationManager;
import com.globalinsights.app.data.FirebaseNotesManager;
import com.globalinsights.app.data.GeminiChatManager;
import com.globalinsights.app.data.Note;
import com.globalinsights.app.data.PreferencesManager;
import com.globalinsights.app.data.ReportStorageManager;
import com.globalinsights.app.data.UserLocation;
import com.globalinsights.app.data.WorldBankDataService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "GlobalInsightsApp";

    private AutoCompleteTextView etSearchCountry;
    private ProgressBar progressBarLoading;
    private CardView cardCountryAnalytics;
    private TextView tvCountryName;
    private TextView tvCountryCode;
    private TextView tvDataYear;
    private TextView tvPopulation;
    private TextView tvGdp;
    private ImageButton btnFavoriteCountry;

    private TextView tvNoFavorites;
    private View hsvFavorites;
    private LinearLayout llFavoritesContainer;

    private TextView tvNoReports;
    private LinearLayout llReportsContainer;

    private TextView[] countryChips;
    private String activeCountryCode = "IND";
    private String activeCountryName = "India";
    private String activeDataYear = "N/A";
    private String currentPopulationText = "";
    private String currentGdpText = "";
    private boolean isSyncingData = false;

    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private boolean isTtsReady = false;

    private DatabaseHelper databaseHelper;
    private PreferencesManager preferencesManager;
    private GeminiChatManager geminiChatManager;
    private Markwon markwon;
    private AlertDialog activeDialog = null;
    private final CountryBroadcastReceiver countryReceiver = new CountryBroadcastReceiver();

    private GoogleMap googleMapInstance = null;
    private Marker currentMapMarker = null;
    private LatLng latestLocationLatLng = null;
    private boolean isLocationSharingActive = false;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ListenerRegistration firestoreLocationListener;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> countryCodeMap = new HashMap<>();
    private final List<WorldBankCountry> allCountriesList = new ArrayList<>();

    public static class WorldBankCountry {
        public final String id;
        public final String name;

        public WorldBankCountry(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private interface Runnable1<T> {
        void accept(T data);
    }

    // Speech-to-Text ActivityResultLauncher for RecognizerIntent
    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedQuery = matches.get(0);
                        etSearchCountry.setText(recognizedQuery);
                        handleCountryQuery(recognizedQuery);
                    } else {
                        Toast.makeText(this, "No speech recognized. Trying direct microphone listener...", Toast.LENGTH_SHORT).show();
                        startDirectSpeechRecognizer();
                    }
                } else if (result.getResultCode() == RESULT_CANCELED) {
                    Toast.makeText(this, "Voice search cancelled.", Toast.LENGTH_SHORT).show();
                } else {
                    startDirectSpeechRecognizer();
                }
            });

    // Permission launcher for RECORD_AUDIO
    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchVoiceSearch();
                } else {
                    Toast.makeText(this, "Microphone permission is required for Voice Search.", Toast.LENGTH_LONG).show();
                }
            });

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Notification permission denied. Background alerts will be shown as Toasts.", Toast.LENGTH_SHORT).show();
                }
            });

    // Permission launcher for Location (FINE & COARSE)
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                Boolean fineGranted = permissions.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarseGranted = permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fineGranted) || Boolean.TRUE.equals(coarseGranted)) {
                    Toast.makeText(this, "Location permission granted. Tap 'Start Sharing' to share location.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Location permission is required for Real-Time Map sharing.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Init Local Data Managers
        databaseHelper = new DatabaseHelper(this);
        preferencesManager = new PreferencesManager(this);
        geminiChatManager = new GeminiChatManager();
        markwon = Markwon.create(this);

        // Apply saved Theme preference BEFORE inflating UI
        int savedNightMode = preferencesManager.getNightMode();
        if (savedNightMode != -1) {
            AppCompatDelegate.setDefaultNightMode(savedNightMode);
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        // Log Firebase Diagnostic Details
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            Log.d(TAG, "=== FIREBASE DIAGNOSTIC INFO ===");
            Log.d(TAG, "Firebase Project ID: " + (app.getOptions() != null ? app.getOptions().getProjectId() : "null"));
            Log.d(TAG, "Firebase Application ID: " + (app.getOptions() != null ? app.getOptions().getApplicationId() : "null"));
        } catch (Exception ex) {
            Log.e(TAG, "FirebaseApp initialization check error", ex);
        }

        // Firebase Anonymous Authentication
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "Current FirebaseUser is NULL on startup. Executing signInAnonymously()...");
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() != null) {
                            Log.d(TAG, "FirebaseAuth.signInAnonymously SUCCESS! User UID: " + authResult.getUser().getUid() + ", IsAnonymous: " + authResult.getUser().isAnonymous());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "FirebaseAuth.signInAnonymously FAILURE!", e);
                        if (e instanceof FirebaseAuthException) {
                            FirebaseAuthException fae = (FirebaseAuthException) e;
                            Log.e(TAG, "Firebase Auth Error Code: " + fae.getErrorCode() + ", Message: " + fae.getMessage());
                        }
                    });
        } else {
            Log.d(TAG, "Current FirebaseUser is already authenticated on startup! UID: " + currentUser.getUid() + ", IsAnonymous: " + currentUser.isAnonymous());
        }

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Ensure Light/Dark Status & Nav Bar Contrast based on active theme
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(!isNight);
            controller.setAppearanceLightNavigationBars(!isNight);
        }

        // Apply WindowInsets padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashboard_main), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initCountryMap();

        // Initialize Views
        etSearchCountry = findViewById(R.id.etSearchCountry);
        ImageButton btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        progressBarLoading = findViewById(R.id.progressBarLoading);
        cardCountryAnalytics = findViewById(R.id.cardCountryAnalytics);
        tvCountryName = findViewById(R.id.tvCountryName);
        tvCountryCode = findViewById(R.id.tvCountryCode);
        tvDataYear = findViewById(R.id.tvDataYear);
        tvPopulation = findViewById(R.id.tvPopulation);
        tvGdp = findViewById(R.id.tvGdp);
        btnFavoriteCountry = findViewById(R.id.btnFavoriteCountry);

        tvNoFavorites = findViewById(R.id.tvNoFavorites);
        hsvFavorites = findViewById(R.id.hsvFavorites);
        llFavoritesContainer = findViewById(R.id.llFavoritesContainer);

        tvNoReports = findViewById(R.id.tvNoReports);
        llReportsContainer = findViewById(R.id.llReportsContainer);

        View btnReadAloud = findViewById(R.id.btnReadAloud);
        View btnSaveReport = findViewById(R.id.btnSaveReport);
        View btnSyncData = findViewById(R.id.btnSyncData);
        View btnSmartNotes = findViewById(R.id.btnSmartNotes);
        View btnLiveMaps = findViewById(R.id.btnLiveMaps);

        // Init Country Filter Chips
        TextView chipIndia = findViewById(R.id.chipIndia);
        TextView chipUSA = findViewById(R.id.chipUSA);
        TextView chipUK = findViewById(R.id.chipUK);
        TextView chipChina = findViewById(R.id.chipChina);
        TextView chipGermany = findViewById(R.id.chipGermany);
        TextView chipJapan = findViewById(R.id.chipJapan);
        TextView chipFrance = findViewById(R.id.chipFrance);
        TextView chipBrazil = findViewById(R.id.chipBrazil);

        countryChips = new TextView[]{chipIndia, chipUSA, chipUK, chipChina, chipGermany, chipJapan, chipFrance, chipBrazil};

        setupChipListeners(chipIndia, "India", "IND");
        setupChipListeners(chipUSA, "USA", "USA");
        setupChipListeners(chipUK, "UK", "GBR");
        setupChipListeners(chipChina, "China", "CHN");
        setupChipListeners(chipGermany, "Germany", "DEU");
        setupChipListeners(chipJapan, "Japan", "JPN");
        setupChipListeners(chipFrance, "France", "FRA");
        setupChipListeners(chipBrazil, "Brazil", "BRA");

        // AutoComplete Search Listeners
        etSearchCountry.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = (String) parent.getItemAtPosition(position);
            handleCountryQuery(selectedItem);
        });

        etSearchCountry.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handleCountryQuery(etSearchCountry.getText().toString());
                return true;
            }
            return false;
        });

        btnVoiceSearch.setOnClickListener(v -> checkAudioPermissionAndSearch());

        // Live Maps Action
        if (btnLiveMaps != null) {
            btnLiveMaps.setOnClickListener(v -> showLiveMapsDialog());
        }

        // Smart Notes Action
        if (btnSmartNotes != null) {
            btnSmartNotes.setOnClickListener(v -> showSmartNotesDialog());
        }

        // Home Header Sync / Refresh Data Button
        if (btnSyncData != null) {
            btnSyncData.setOnClickListener(v -> {
                if (isSyncingData) {
                    Toast.makeText(this, "Background World Bank sync is already running...", Toast.LENGTH_SHORT).show();
                } else {
                    isSyncingData = true;
                    Toast.makeText(this, "Syncing live World Bank data for " + activeCountryName + "...", Toast.LENGTH_SHORT).show();
                    Intent serviceIntent = new Intent(this, WorldBankDataService.class);
                    serviceIntent.putExtra("country_code", activeCountryCode);
                    serviceIntent.putExtra("country_name", activeCountryName);
                    startService(serviceIntent);
                }
            });
        }

        // Favorite Toggle Action (SQLite)
        btnFavoriteCountry.setOnClickListener(v -> toggleFavoriteCountry());

        // Save Country Report Action (Internal Storage + Broadcast + Notification)
        btnSaveReport.setOnClickListener(v -> saveCurrentCountryReport());

        // Text-to-Speech Engine
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = textToSpeech.setLanguage(Locale.US);
                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true;
                }
            }
        });

        btnReadAloud.setOnClickListener(v -> speakCountryInsights());

        // Setup Bottom Navigation Bar Listeners
        setupBottomNavigation();

        // Refresh UI from local storage
        refreshFavoritesUI();
        refreshSavedReportsUI();

        // Asynchronously fetch complete World Bank country catalog
        fetchAllWorldBankCountries();

        // Check if launched from Notification Intent
        checkNotificationDeepLink(getIntent());

        // Restore preferred country from SharedPreferences if no deep link
        if (!getIntent().hasExtra("country_code")) {
            String startCode = preferencesManager.getPreferredCountryCode();
            String startName = preferencesManager.getPreferredCountryName();
            highlightChipForCode(startCode);
            fetchWorldBankData(startCode, startName);
        }
    }

    private void updateMapMarkerAndCamera(LatLng pos) {
        latestLocationLatLng = pos;
        if (googleMapInstance == null || pos == null || !isLocationSharingActive) return;

        Log.d(TAG, "Updating Google Map Marker to: lat=" + pos.latitude + ", lng=" + pos.longitude);

        if (currentMapMarker == null) {
            currentMapMarker = googleMapInstance.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Live Location (" + activeCountryCode + ")"));
        } else {
            currentMapMarker.setPosition(pos);
        }

        googleMapInstance.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
    }

    private void showLiveMapsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_live_maps, null);

        View root = dialogView.findViewById(R.id.liveMapsRoot);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseLiveMaps);
        TextView tvStatus = dialogView.findViewById(R.id.tvMapSyncStatus);
        MaterialButton btnToggleSharing = dialogView.findViewById(R.id.btnToggleLocationSharing);
        MapView mapView = dialogView.findViewById(R.id.mapView);
        TextView tvUid = dialogView.findViewById(R.id.tvMapUserUid);
        TextView tvLat = dialogView.findViewById(R.id.tvMapLat);
        TextView tvLng = dialogView.findViewById(R.id.tvMapLng);
        TextView tvLastSynced = dialogView.findViewById(R.id.tvMapLastSynced);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "Unauthenticated";
        if (tvUid != null) {
            tvUid.setText(String.format(Locale.US, "UID: %s", uid.length() > 6 ? uid.substring(0, 6) : uid));
        }

        // Explicitly reset initial state when dialog opens
        isLocationSharingActive = false;
        latestLocationLatLng = null;

        if (currentMapMarker != null) {
            try {
                currentMapMarker.remove();
            } catch (Exception ignored) {}
            currentMapMarker = null;
        }

        tvLat.setText("N/A");
        tvLng.setText("N/A");
        tvLastSynced.setText("Last Synced: Never");
        if (tvStatus != null) {
            tvStatus.setText(R.string.map_sharing_off);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        if (btnToggleSharing != null) {
            btnToggleSharing.setText(R.string.btn_start_sharing);
            btnToggleSharing.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_GlobalInsights_FullScreenDialog)
                .setView(dialogView)
                .create();

        activeDialog = dialog;

        // Initialize MapView
        mapView.onCreate(null);
        mapView.onResume();
        mapView.getMapAsync(map -> {
            googleMapInstance = map;
            try {
                map.getUiSettings().setZoomControlsEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
            } catch (Exception ignored) {}

            if (latestLocationLatLng != null && isLocationSharingActive) {
                updateMapMarkerAndCamera(latestLocationLatLng);
            }
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Attach Real-Time Firestore Snapshot Listener (only updates UI when sharing is active!)
        firestoreLocationListener = FirebaseLocationManager.listenToLocation(new FirebaseLocationManager.LocationUpdateCallback() {
            @Override
            public void onLocationUpdated(UserLocation location) {
                if (location == null || !isLocationSharingActive || !location.isSharing()) return;

                Log.d(TAG, "Firestore Snapshot Received: Lat=" + location.getLatitude() + ", Lng=" + location.getLongitude() + ", Sharing=" + location.isSharing());

                tvLat.setText(String.format(Locale.US, "%.5f", location.getLatitude()));
                tvLng.setText(String.format(Locale.US, "%.5f", location.getLongitude()));

                String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(location.getTimestamp()));
                tvLastSynced.setText(String.format(Locale.US, "Last Synced: %s", timeStr));

                tvStatus.setText("GPS Active • Syncing to Firestore");
                tvStatus.setTextColor(ContextCompat.getColor(DashboardActivity.this, R.color.primary));

                // Update Marker on Google Map
                updateMapMarkerAndCamera(new LatLng(location.getLatitude(), location.getLongitude()));
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Location snapshot listener error: " + errorMessage);
                if (errorMessage.contains("PERMISSION_DENIED") || errorMessage.contains("permission")) {
                    Toast.makeText(DashboardActivity.this, "Firestore Permission Error: Check Security Rules for users/{uid}/location/current", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Start / Stop Location Sharing Toggle
        btnToggleSharing.setOnClickListener(v -> {
            if (!isLocationSharingActive) {
                // Check Location Permission
                boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

                if (!fine && !coarse) {
                    locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
                    return;
                }

                isLocationSharingActive = true;
                btnToggleSharing.setText(R.string.btn_stop_sharing);
                btnToggleSharing.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.accent));
                tvStatus.setText("GPS Active • Syncing to Firestore");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary));

                Log.d(TAG, "Location request started with priority HIGH_ACCURACY (interval 3000ms)");

                // Helper action to process a Location object and update local UI + Firestore
                Runnable1<Location> processLocationAction = (loc) -> {
                    if (loc == null || !isLocationSharingActive) return;
                    Log.d(TAG, "Processing Location update: lat=" + loc.getLatitude() + ", lng=" + loc.getLongitude());

                    tvLat.setText(String.format(Locale.US, "%.5f", loc.getLatitude()));
                    tvLng.setText(String.format(Locale.US, "%.5f", loc.getLongitude()));

                    updateMapMarkerAndCamera(new LatLng(loc.getLatitude(), loc.getLongitude()));

                    FirebaseLocationManager.updateLocation(loc.getLatitude(), loc.getLongitude(), true, activeCountryCode, new FirebaseLocationManager.ActionCallback() {
                        @Override
                        public void onSuccess() {
                            String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                            tvLastSynced.setText(String.format(Locale.US, "Last Synced: %s", timeStr));
                            Log.d(TAG, "Firestore write CONFIRMED! Last Synced updated to " + timeStr);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Firestore Location Write ERROR: " + errorMessage);
                            Toast.makeText(DashboardActivity.this, "Firestore Write Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                };

                // 1. Immediately fetch last known location to populate UI & Firestore without waiting for next GPS pulse
                try {
                    fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                        if (loc != null && isLocationSharingActive) {
                            Log.d(TAG, "getLastLocation SUCCESS: lat=" + loc.getLatitude() + ", lng=" + loc.getLongitude());
                            processLocationAction.accept(loc);
                        } else {
                            Log.w(TAG, "getLastLocation returned null or sharing inactive.");
                        }
                    });
                } catch (SecurityException se) {
                    Log.e(TAG, "SecurityException on getLastLocation", se);
                }

                // 2. Request periodic updates with 0 meters min distance so emulator static coordinates are continuously pushed
                LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                        .setMinUpdateIntervalMillis(1000L)
                        .setMinUpdateDistanceMeters(0f)
                        .build();

                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        if (locationResult == null || !isLocationSharingActive) return;
                        for (Location loc : locationResult.getLocations()) {
                            if (loc != null) {
                                Log.d(TAG, "Location callback received: lat=" + loc.getLatitude() + ", lng=" + loc.getLongitude());
                                processLocationAction.accept(loc);
                            }
                        }
                    }
                };

                try {
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                    Toast.makeText(this, "Live Location Sharing Started", Toast.LENGTH_SHORT).show();
                } catch (SecurityException se) {
                    Log.e(TAG, "Location permission missing", se);
                }

            } else {
                // Stop Sharing
                isLocationSharingActive = false;
                btnToggleSharing.setText(R.string.btn_start_sharing);
                btnToggleSharing.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
                tvStatus.setText(R.string.map_sharing_off);
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

                if (fusedLocationClient != null && locationCallback != null) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                }

                // Clear marker and lat/lng UI
                if (currentMapMarker != null) {
                    try {
                        currentMapMarker.remove();
                    } catch (Exception ignored) {}
                    currentMapMarker = null;
                }
                latestLocationLatLng = null;

                tvLat.setText("N/A");
                tvLng.setText("N/A");
                tvLastSynced.setText("Last Synced: Sharing Off");

                FirebaseLocationManager.updateLocation(0, 0, false, activeCountryCode, null);
                Toast.makeText(this, "Location Sharing Stopped.", Toast.LENGTH_SHORT).show();
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Safely Cleanup MapView, FusedLocation, and Firestore Snapshot Listener on Dialog Dismiss
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;

            isLocationSharingActive = false;

            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }

            if (firestoreLocationListener != null) {
                firestoreLocationListener.remove();
                firestoreLocationListener = null;
            }

            if (mapView != null) {
                try {
                    mapView.onPause();
                    mapView.onStop();
                    mapView.onDestroy();
                } catch (Exception ignored) {}
            }

            if (currentMapMarker != null) {
                try {
                    currentMapMarker.remove();
                } catch (Exception ignored) {}
                currentMapMarker = null;
            }
            latestLocationLatLng = null;
            googleMapInstance = null;
        });

        dialog.show();
    }

    private void showSmartNotesDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_smart_notes, null);

        View root = dialogView.findViewById(R.id.smartNotesRoot);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseSmartNotes);
        TextView tvAuthStatus = dialogView.findViewById(R.id.tvNotesAuthStatus);
        MaterialButton btnNewNote = dialogView.findViewById(R.id.btnCreateNewNote);
        ProgressBar pbLoading = dialogView.findViewById(R.id.pbNotesLoading);
        TextView tvNoNotes = dialogView.findViewById(R.id.tvNoNotes);
        LinearLayout llContainer = dialogView.findViewById(R.id.llSmartNotesContainer);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_GlobalInsights_FullScreenDialog)
                .setView(dialogView)
                .create();

        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnNewNote.setOnClickListener(v -> showEditNoteDialog(null, () -> refreshSmartNotesUI(llContainer, pbLoading, tvNoNotes)));

        // Clear previous state
        tvNoNotes.setText(R.string.no_notes_found);
        if (tvAuthStatus != null) {
            tvAuthStatus.setText(R.string.notes_subtitle);
        }

        // Verify or attempt Anonymous Sign-In before allowing Firestore access
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            pbLoading.setVisibility(View.VISIBLE);
            btnNewNote.setEnabled(false);

            Log.d(TAG, "showSmartNotesDialog: FirebaseUser is NULL. Executing signInAnonymously()...");
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        pbLoading.setVisibility(View.GONE);
                        btnNewNote.setEnabled(true);
                        if (tvAuthStatus != null) {
                            tvAuthStatus.setText(R.string.notes_subtitle);
                        }
                        if (authResult.getUser() != null) {
                            Log.d(TAG, "signInAnonymously SUCCESS on Smart Notes launch! UID: " + authResult.getUser().getUid());
                        }
                        refreshSmartNotesUI(llContainer, pbLoading, tvNoNotes);
                    })
                    .addOnFailureListener(e -> {
                        pbLoading.setVisibility(View.GONE);
                        btnNewNote.setEnabled(false);
                        tvNoNotes.setVisibility(View.VISIBLE);
                        tvNoNotes.setText(R.string.no_notes_found);

                        Log.e(TAG, "signInAnonymously FAILURE on Smart Notes launch!", e);
                        if (e instanceof FirebaseAuthException) {
                            FirebaseAuthException fae = (FirebaseAuthException) e;
                            Log.e(TAG, "FirebaseAuth Error Code: " + fae.getErrorCode() + ", Class: " + e.getClass().getName());
                        }
                        Toast.makeText(this, "Firebase Authentication required.", Toast.LENGTH_SHORT).show();
                    });
        } else {
            btnNewNote.setEnabled(true);
            Log.d(TAG, "showSmartNotesDialog: FirebaseUser authenticated! UID: " + currentUser.getUid() + ", IsAnonymous: " + currentUser.isAnonymous());
            refreshSmartNotesUI(llContainer, pbLoading, tvNoNotes);
        }

        dialog.show();
    }

    private void refreshSmartNotesUI(LinearLayout llContainer, ProgressBar pbLoading, TextView tvNoNotes) {
        if (llContainer == null || pbLoading == null || tvNoNotes == null) return;

        // Strict security check: Do not attempt Firestore fetch if user is unauthenticated
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            pbLoading.setVisibility(View.GONE);
            tvNoNotes.setVisibility(View.VISIBLE);
            tvNoNotes.setText(R.string.no_notes_found);
            Log.w(TAG, "refreshSmartNotesUI: Skipping Firestore fetch because FirebaseUser is null.");
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        tvNoNotes.setVisibility(View.GONE);
        llContainer.removeAllViews();

        FirebaseNotesManager.fetchNotes(new FirebaseNotesManager.NotesCallback() {
            @Override
            public void onSuccess(List<Note> notes) {
                pbLoading.setVisibility(View.GONE);
                if (notes == null || notes.isEmpty()) {
                    tvNoNotes.setVisibility(View.VISIBLE);
                    tvNoNotes.setText(R.string.no_notes_found);
                } else {
                    tvNoNotes.setVisibility(View.GONE);
                    for (Note note : notes) {
                        renderNoteCard(llContainer, note, () -> refreshSmartNotesUI(llContainer, pbLoading, tvNoNotes));
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                pbLoading.setVisibility(View.GONE);
                tvNoNotes.setVisibility(View.VISIBLE);
                tvNoNotes.setText(R.string.no_notes_found);
                Log.e(TAG, "Error fetching notes from Firestore: " + errorMessage);
            }
        });
    }

    private void renderNoteCard(LinearLayout container, Note note, Runnable onRefresh) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(20f);
        card.setCardElevation(2f);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_card));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.border_color));
        card.setStrokeWidth(2);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        // Header Row: Title & Badge
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(note.getTitle());
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleParams);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(note.getCountryCode() != null ? note.getCountryCode() : "GLOBAL");
        tvBadge.setBackgroundResource(R.drawable.bg_chip_selected);
        tvBadge.setPadding(16, 6, 16, 6);
        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.white));
        tvBadge.setTextSize(11f);
        tvBadge.setTypeface(null, Typeface.BOLD);

        topRow.addView(tvTitle);
        topRow.addView(tvBadge);

        // Date
        String dateStr = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(new Date(note.getTimestamp()));
        TextView tvDate = new TextView(this);
        tvDate.setText(String.format(Locale.US, "Saved: %s", dateStr));
        tvDate.setTextSize(11f);
        tvDate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvDate.setPadding(0, 4, 0, 8);

        // AI Summary Block if available
        View summaryCard = null;
        if (note.getSummary() != null && !note.getSummary().trim().isEmpty() && !note.getSummary().contains("import com.")) {
            MaterialCardView summaryBox = new MaterialCardView(this);
            LinearLayout.LayoutParams sumParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            sumParams.setMargins(0, 8, 0, 12);
            summaryBox.setLayoutParams(sumParams);
            summaryBox.setRadius(12f);
            summaryBox.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_light));
            summaryBox.setStrokeColor(ContextCompat.getColor(this, R.color.border_color));
            summaryBox.setStrokeWidth(1);

            LinearLayout sumLayout = new LinearLayout(this);
            sumLayout.setOrientation(LinearLayout.VERTICAL);
            sumLayout.setPadding(20, 16, 20, 16);

            TextView tvSumTag = new TextView(this);
            tvSumTag.setText(getString(R.string.heading_ai_summary));
            tvSumTag.setTextSize(10f);
            tvSumTag.setTypeface(null, Typeface.BOLD);
            tvSumTag.setTextColor(ContextCompat.getColor(this, R.color.primary));

            TextView tvSumText = new TextView(this);
            tvSumText.setText(note.getSummary());
            tvSumText.setTextSize(12f);
            tvSumText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvSumText.setPadding(0, 4, 0, 0);

            sumLayout.addView(tvSumTag);
            sumLayout.addView(tvSumText);
            summaryBox.addView(sumLayout);
            summaryCard = summaryBox;
        }

        // Note Content
        TextView tvContent = new TextView(this);
        tvContent.setText(note.getContent());
        tvContent.setTextSize(13f);
        tvContent.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvContent.setPadding(0, 4, 0, 12);

        // Actions Row
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton btnEdit = new MaterialButton(this);
        btnEdit.setText("Edit");
        btnEdit.setTextSize(12f);
        btnEdit.setCornerRadius(16);
        btnEdit.setOnClickListener(v -> showEditNoteDialog(note, onRefresh));

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText(getString(R.string.btn_delete));
        btnDelete.setTextSize(12f);
        btnDelete.setCornerRadius(16);
        btnDelete.setBackgroundColor(ContextCompat.getColor(this, R.color.border_color));
        btnDelete.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        btnDelete.setOnClickListener(v -> {
            FirebaseNotesManager.deleteNote(note.getId(), new FirebaseNotesManager.NoteActionCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(DashboardActivity.this, "Note deleted from Cloud Firestore", Toast.LENGTH_SHORT).show();
                    onRefresh.run();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(DashboardActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        LinearLayout.LayoutParams btnParams1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnParams1.setMargins(0, 0, 12, 0);
        btnEdit.setLayoutParams(btnParams1);
        btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        actionLayout.addView(btnEdit);
        actionLayout.addView(btnDelete);

        layout.addView(topRow);
        layout.addView(tvDate);
        if (summaryCard != null) layout.addView(summaryCard);
        layout.addView(tvContent);
        layout.addView(actionLayout);
        card.addView(layout);

        container.addView(card);
    }

    private void showEditNoteDialog(Note noteToEdit, Runnable onSaveSuccess) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_note, null);

        TextView tvHeading = dialogView.findViewById(R.id.tvEditNoteHeading);
        EditText etTitle = dialogView.findViewById(R.id.etNoteTitle);
        AutoCompleteTextView spinnerCountry = dialogView.findViewById(R.id.spinnerNoteCountry);
        EditText etContent = dialogView.findViewById(R.id.etNoteContent);
        MaterialButton btnAiSummary = dialogView.findViewById(R.id.btnGenerateAiSummary);
        ProgressBar pbAiLoading = dialogView.findViewById(R.id.pbAiSummaryLoading);
        TextView tvSummary = dialogView.findViewById(R.id.tvNoteSummary);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelEditNote);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSaveNoteToFirestore);

        // Pre-populate Country Dropdown
        if (!allCountriesList.isEmpty()) {
            List<String> displayList = new ArrayList<>();
            for (WorldBankCountry c : allCountriesList) {
                displayList.add(c.name + " (" + c.id + ")");
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, displayList);
            spinnerCountry.setAdapter(adapter);
        }

        boolean isEditing = (noteToEdit != null);
        if (isEditing) {
            tvHeading.setText(getString(R.string.edit_note_title));
            etTitle.setText(noteToEdit.getTitle());
            spinnerCountry.setText(noteToEdit.getCountryCode(), false);
            etContent.setText(noteToEdit.getContent());
            if (noteToEdit.getSummary() != null && !noteToEdit.getSummary().isEmpty() && !noteToEdit.getSummary().contains("import com.")) {
                tvSummary.setText(noteToEdit.getSummary());
            } else {
                tvSummary.setText("");
            }
        } else {
            tvHeading.setText(getString(R.string.create_note_title));
            etTitle.setText("");
            etContent.setText("");
            tvSummary.setText("");
            spinnerCountry.setText(String.format(Locale.US, "%s (%s)", activeCountryName, activeCountryCode), false);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Generate AI Summary
        btnAiSummary.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String content = etContent.getText().toString().trim();

            if (TextUtils.isEmpty(content)) {
                Toast.makeText(this, "Please enter note content before generating AI summary.", Toast.LENGTH_SHORT).show();
                return;
            }

            pbAiLoading.setVisibility(View.VISIBLE);
            geminiChatManager.generateNoteSummary(title, content, new GeminiChatManager.SummaryCallback() {
                @Override
                public void onSuccess(String summaryText) {
                    pbAiLoading.setVisibility(View.GONE);
                    tvSummary.setText(summaryText);
                    Toast.makeText(DashboardActivity.this, "AI Summary Generated!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String errorMessage) {
                    pbAiLoading.setVisibility(View.GONE);
                    tvSummary.setText("");
                    Toast.makeText(DashboardActivity.this, "AI Summary Notice: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Save Note to Cloud Firestore
        btnSave.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Firebase Authentication required. Please wait for sign-in.", Toast.LENGTH_SHORT).show();
                return;
            }

            String title = etTitle.getText().toString().trim();
            String rawCountry = spinnerCountry.getText().toString().trim();
            String content = etContent.getText().toString().trim();
            String summary = tvNoteSummaryText(tvSummary);

            if (TextUtils.isEmpty(title)) {
                Toast.makeText(this, "Please enter a title for your note.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (TextUtils.isEmpty(content)) {
                Toast.makeText(this, "Please enter note content.", Toast.LENGTH_SHORT).show();
                return;
            }

            String resolvedCode = resolveCountryCodeFromInput(rawCountry);

            Note note = isEditing ? noteToEdit : new Note();
            note.setTitle(title);
            note.setCountryCode(resolvedCode);
            note.setContent(content);
            note.setSummary(summary);
            note.setTimestamp(System.currentTimeMillis());

            btnSave.setEnabled(false);
            FirebaseNotesManager.saveNote(note, new FirebaseNotesManager.NoteActionCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(DashboardActivity.this, "Saved note to Cloud Firestore!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    if (onSaveSuccess != null) onSaveSuccess.run();
                }

                @Override
                public void onError(String errorMessage) {
                    btnSave.setEnabled(true);
                    Toast.makeText(DashboardActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private String tvNoteSummaryText(TextView tvSummary) {
        if (tvSummary == null || tvSummary.getText() == null) return "";
        String txt = tvSummary.getText().toString().trim();
        if (txt.startsWith("No AI summary") || txt.startsWith("AI Summary Notice") || txt.contains("import com.") || txt.contains("Files Modified") || txt.contains("Root Cause")) return "";
        return txt;
    }

    private void checkNotificationDeepLink(Intent intent) {
        if (intent != null && intent.hasExtra("country_code")) {
            String code = intent.getStringExtra("country_code");
            String name = intent.getStringExtra("country_name");
            if (name == null) name = code;

            // Dismiss any active dialog (e.g. Compare or AI Chat dialog)
            if (activeDialog != null && activeDialog.isShowing()) {
                activeDialog.dismiss();
                activeDialog = null;
            }

            // Visually select Home tab
            updateBottomNavSelection(R.id.navTabHome);

            etSearchCountry.setText(name);
            highlightChipForCode(code);
            fetchWorldBankData(code, name);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkNotificationDeepLink(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        CountryBroadcastReceiver.setSyncStatusListener(() -> isSyncingData = false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(CountryBroadcastReceiver.ACTION_REPORT_SAVED);
        filter.addAction(CountryBroadcastReceiver.ACTION_SYNC_COMPLETED);
        filter.addAction(CountryBroadcastReceiver.ACTION_SYNC_FAILED);
        ContextCompat.registerReceiver(this, countryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        CountryBroadcastReceiver.setSyncStatusListener(null);
        try {
            unregisterReceiver(countryReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    private void fetchAllWorldBankCountries() {
        executorService.execute(() -> {
            String catalogUrl = "https://api.worldbank.org/v2/country?format=json&per_page=400";
            Log.d(TAG, "Fetching World Bank country catalog from: " + catalogUrl);
            String jsonResult = makeHttpGetRequest(catalogUrl);

            if (jsonResult != null) {
                try {
                    JSONArray rootArray = new JSONArray(jsonResult);
                    if (rootArray.length() >= 2 && !rootArray.isNull(1)) {
                        JSONArray countryArray = rootArray.getJSONArray(1);
                        List<WorldBankCountry> parsedCountries = new ArrayList<>();
                        List<String> displayList = new ArrayList<>();

                        for (int i = 0; i < countryArray.length(); i++) {
                            JSONObject obj = countryArray.getJSONObject(i);
                            String id = obj.optString("id", "").trim();
                            String name = obj.optString("name", "").trim();

                            if (!id.isEmpty() && !name.isEmpty()) {
                                parsedCountries.add(new WorldBankCountry(id, name));
                                displayList.add(name + " (" + id + ")");
                            }
                        }

                        Log.d(TAG, "Parsed " + parsedCountries.size() + " countries from World Bank API catalog.");

                        mainHandler.post(() -> {
                            allCountriesList.clear();
                            allCountriesList.addAll(parsedCountries);

                            for (WorldBankCountry c : parsedCountries) {
                                countryCodeMap.put(c.name.toUpperCase(Locale.US), c.id);
                                countryCodeMap.put(c.id.toUpperCase(Locale.US), c.id);
                            }

                            ArrayAdapter<String> autoAdapter = new ArrayAdapter<>(
                                    DashboardActivity.this,
                                    android.R.layout.simple_dropdown_item_1line,
                                    displayList
                            );
                            etSearchCountry.setAdapter(autoAdapter);
                        });
                    } else {
                        Log.e(TAG, "World Bank country catalog array structure invalid.");
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error in World Bank catalog", e);
                }
            } else {
                Log.e(TAG, "Failed to download World Bank country catalog (network error).");
            }
        });
    }

    private void setupBottomNavigation() {
        View navTabHome = findViewById(R.id.navTabHome);
        View navTabExplore = findViewById(R.id.navTabExplore);
        View navTabCompare = findViewById(R.id.navTabCompare);
        View navTabProfile = findViewById(R.id.navTabProfile);

        navTabHome.setOnClickListener(v -> {
            updateBottomNavSelection(R.id.navTabHome);
            etSearchCountry.setText("");
            String startCode = preferencesManager.getPreferredCountryCode();
            String startName = preferencesManager.getPreferredCountryName();
            highlightChipForCode(startCode);
            fetchWorldBankData(startCode, startName);
        });

        // Launch Gemini AI Assistant Chatbot
        navTabExplore.setOnClickListener(v -> {
            updateBottomNavSelection(R.id.navTabExplore);
            showAiChatDialog();
        });

        // Launch Real Country Comparison Engine
        navTabCompare.setOnClickListener(v -> {
            updateBottomNavSelection(R.id.navTabCompare);
            showCompareDialog();
        });

        // Profile / Preferences Dialog (SharedPreferences)
        navTabProfile.setOnClickListener(v -> {
            updateBottomNavSelection(R.id.navTabProfile);
            showPreferencesDialog();
        });
    }

    private void updateBottomNavSelection(int selectedId) {
        ImageView ivHome = findViewById(R.id.ivNavHome);
        TextView tvHome = findViewById(R.id.tvNavHome);

        ImageView ivExplore = findViewById(R.id.ivNavExplore);
        TextView tvExplore = findViewById(R.id.tvNavExplore);

        ImageView ivCompare = findViewById(R.id.ivNavCompare);
        TextView tvCompare = findViewById(R.id.tvNavCompare);

        ImageView ivProfile = findViewById(R.id.ivNavProfile);
        TextView tvProfile = findViewById(R.id.tvNavProfile);

        int activeColor = ContextCompat.getColor(this, R.color.primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);

        if (ivHome != null && tvHome != null) {
            ivHome.setColorFilter(selectedId == R.id.navTabHome ? activeColor : inactiveColor);
            tvHome.setTextColor(selectedId == R.id.navTabHome ? activeColor : inactiveColor);
        }

        if (ivExplore != null && tvExplore != null) {
            ivExplore.setColorFilter(selectedId == R.id.navTabExplore ? activeColor : inactiveColor);
            tvExplore.setTextColor(selectedId == R.id.navTabExplore ? activeColor : inactiveColor);
        }

        if (ivCompare != null && tvCompare != null) {
            ivCompare.setColorFilter(selectedId == R.id.navTabCompare ? activeColor : inactiveColor);
            tvCompare.setTextColor(selectedId == R.id.navTabCompare ? activeColor : inactiveColor);
        }

        if (ivProfile != null && tvProfile != null) {
            ivProfile.setColorFilter(selectedId == R.id.navTabProfile ? activeColor : inactiveColor);
            tvProfile.setTextColor(selectedId == R.id.navTabProfile ? activeColor : inactiveColor);
        }
    }

    private void showCompareDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_compare, null);

        View compareRoot = dialogView.findViewById(R.id.compareRoot);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseCompare);
        AutoCompleteTextView etC1 = dialogView.findViewById(R.id.etCompareCountry1);
        AutoCompleteTextView etC2 = dialogView.findViewById(R.id.etCompareCountry2);
        MaterialButton btnCompare = dialogView.findViewById(R.id.btnExecuteCompare);
        ProgressBar pbLoading = dialogView.findViewById(R.id.pbCompareLoading);
        TextView tvError = dialogView.findViewById(R.id.tvCompareError);
        LinearLayout layoutResults = dialogView.findViewById(R.id.layoutCompareResults);

        TextView tvC1Name = dialogView.findViewById(R.id.tvC1Name);
        TextView tvC1Code = dialogView.findViewById(R.id.tvC1Code);
        TextView tvC2Name = dialogView.findViewById(R.id.tvC2Name);
        TextView tvC2Code = dialogView.findViewById(R.id.tvC2Code);

        TextView tvC1Pop = dialogView.findViewById(R.id.tvC1Pop);
        TextView tvC2Pop = dialogView.findViewById(R.id.tvC2Pop);
        View barC1Pop = dialogView.findViewById(R.id.barC1Pop);
        View barC2Pop = dialogView.findViewById(R.id.barC2Pop);

        TextView tvC1Gdp = dialogView.findViewById(R.id.tvC1Gdp);
        TextView tvC2Gdp = dialogView.findViewById(R.id.tvC2Gdp);
        View barC1Gdp = dialogView.findViewById(R.id.barC1Gdp);
        View barC2Gdp = dialogView.findViewById(R.id.barC2Gdp);

        TextView tvC1PerCapita = dialogView.findViewById(R.id.tvC1PerCapita);
        TextView tvC2PerCapita = dialogView.findViewById(R.id.tvC2PerCapita);

        TextView tvC1DataYear = dialogView.findViewById(R.id.tvC1DataYear);
        TextView tvC2DataYear = dialogView.findViewById(R.id.tvC2DataYear);

        MaterialButton btnAskAi = dialogView.findViewById(R.id.btnAskAiComparison);

        // Apply System Bar and IME Soft Keyboard Insets
        ViewCompat.setOnApplyWindowInsetsListener(compareRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    Math.max(systemBars.bottom, imeInsets.bottom)
            );
            return WindowInsetsCompat.CONSUMED;
        });

        // Populate AutoCompleteTextView dropdowns
        if (!allCountriesList.isEmpty()) {
            List<String> displayList = new ArrayList<>();
            for (WorldBankCountry c : allCountriesList) {
                displayList.add(c.name + " (" + c.id + ")");
            }
            ArrayAdapter<String> adapter1 = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, displayList);
            ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, displayList);
            etC1.setAdapter(adapter1);
            etC2.setAdapter(adapter2);
        }

        // Default pre-fills: Country 1 -> active country, Country 2 -> USA or India
        String defaultC1Name = activeCountryName;
        String defaultC1Code = activeCountryCode;
        String defaultC2Name = "USA".equalsIgnoreCase(activeCountryCode) ? "India" : "USA";
        String defaultC2Code = "USA".equalsIgnoreCase(activeCountryCode) ? "IND" : "USA";

        etC1.setText(String.format(Locale.US, "%s (%s)", defaultC1Name, defaultC1Code), false);
        etC2.setText(String.format(Locale.US, "%s (%s)", defaultC2Name, defaultC2Code), false);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_GlobalInsights_FullScreenDialog)
                .setView(dialogView)
                .create();

        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Helper to resolve code & name from raw autocomplete text
        Runnable1<Void> executeComparison = (ignored) -> {
            String input1 = etC1.getText().toString().trim();
            String input2 = etC2.getText().toString().trim();

            String code1 = resolveCountryCodeFromInput(input1);
            String name1 = resolveCountryNameFromInput(input1);
            String code2 = resolveCountryCodeFromInput(input2);
            String name2 = resolveCountryNameFromInput(input2);

            if (code1.equalsIgnoreCase(code2)) {
                pbLoading.setVisibility(View.GONE);
                layoutResults.setVisibility(View.GONE);
                tvError.setVisibility(View.VISIBLE);
                tvError.setText("Please select two different countries to compare.");
                return;
            }

            tvError.setVisibility(View.GONE);
            pbLoading.setVisibility(View.VISIBLE);
            layoutResults.setVisibility(View.GONE);

            executorService.execute(() -> {
                String popUrl1 = "https://api.worldbank.org/v2/country/" + code1 + "/indicator/SP.POP.TOTL?format=json&mrnev=1";
                String gdpUrl1 = "https://api.worldbank.org/v2/country/" + code1 + "/indicator/NY.GDP.MKTP.CD?format=json&mrnev=1";

                String popUrl2 = "https://api.worldbank.org/v2/country/" + code2 + "/indicator/SP.POP.TOTL?format=json&mrnev=1";
                String gdpUrl2 = "https://api.worldbank.org/v2/country/" + code2 + "/indicator/NY.GDP.MKTP.CD?format=json&mrnev=1";

                IndicatorResult popRes1 = parseWorldBankJson(makeHttpGetRequest(popUrl1));
                IndicatorResult gdpRes1 = parseWorldBankJson(makeHttpGetRequest(gdpUrl1));

                IndicatorResult popRes2 = parseWorldBankJson(makeHttpGetRequest(popUrl2));
                IndicatorResult gdpRes2 = parseWorldBankJson(makeHttpGetRequest(gdpUrl2));

                mainHandler.post(() -> {
                    pbLoading.setVisibility(View.GONE);

                    String c1Name = !popRes1.countryName.isEmpty() ? popRes1.countryName : name1;
                    String c2Name = !popRes2.countryName.isEmpty() ? popRes2.countryName : name2;

                    tvC1Name.setText(c1Name);
                    tvC1Code.setText(code1);
                    tvC2Name.setText(c2Name);
                    tvC2Code.setText(code2);

                    tvC1Pop.setText(formatPopulation(popRes1.value));
                    tvC2Pop.setText(formatPopulation(popRes2.value));

                    tvC1Gdp.setText(formatGdp(gdpRes1.value));
                    tvC2Gdp.setText(formatGdp(gdpRes2.value));

                    // Calculate GDP per capita (GDP / Population)
                    if (popRes1.value > 0 && gdpRes1.value > 0) {
                        double perCap1 = gdpRes1.value / popRes1.value;
                        tvC1PerCapita.setText(String.format(Locale.US, "$%,d / cap", Math.round(perCap1)));
                    } else {
                        tvC1PerCapita.setText("Data Not Available");
                    }

                    if (popRes2.value > 0 && gdpRes2.value > 0) {
                        double perCap2 = gdpRes2.value / popRes2.value;
                        tvC2PerCapita.setText(String.format(Locale.US, "$%,d / cap", Math.round(perCap2)));
                    } else {
                        tvC2PerCapita.setText("Data Not Available");
                    }

                    tvC1DataYear.setText(String.format(Locale.US, "Data Year: %s", popRes1.dateYear));
                    tvC2DataYear.setText(String.format(Locale.US, "Data Year: %s", popRes2.dateYear));

                    // Relative Population Comparison Bar Ratio
                    double p1 = Math.max(0, popRes1.value);
                    double p2 = Math.max(0, popRes2.value);
                    double totalPop = p1 + p2;
                    float popWeight1 = totalPop > 0 ? (float) (p1 / totalPop) : 0.5f;
                    float popWeight2 = totalPop > 0 ? (float) (p2 / totalPop) : 0.5f;

                    LinearLayout.LayoutParams p1Params = (LinearLayout.LayoutParams) barC1Pop.getLayoutParams();
                    p1Params.weight = popWeight1;
                    barC1Pop.setLayoutParams(p1Params);

                    LinearLayout.LayoutParams p2Params = (LinearLayout.LayoutParams) barC2Pop.getLayoutParams();
                    p2Params.weight = popWeight2;
                    barC2Pop.setLayoutParams(p2Params);

                    // Relative GDP Comparison Bar Ratio
                    double g1 = Math.max(0, gdpRes1.value);
                    double g2 = Math.max(0, gdpRes2.value);
                    double totalGdp = g1 + g2;
                    float gdpWeight1 = totalGdp > 0 ? (float) (g1 / totalGdp) : 0.5f;
                    float gdpWeight2 = totalGdp > 0 ? (float) (g2 / totalGdp) : 0.5f;

                    LinearLayout.LayoutParams g1Params = (LinearLayout.LayoutParams) barC1Gdp.getLayoutParams();
                    g1Params.weight = gdpWeight1;
                    barC1Gdp.setLayoutParams(g1Params);

                    LinearLayout.LayoutParams g2Params = (LinearLayout.LayoutParams) barC2Gdp.getLayoutParams();
                    g2Params.weight = gdpWeight2;
                    barC2Gdp.setLayoutParams(g2Params);

                    layoutResults.setVisibility(View.VISIBLE);
                });
            });
        };

        btnCompare.setOnClickListener(v -> executeComparison.accept(null));

        btnAskAi.setOnClickListener(v -> {
            dialog.dismiss();
            showAiChatDialog();
        });

        // Execute initial comparison on open
        executeComparison.accept(null);

        dialog.show();
    }

    private String resolveCountryCodeFromInput(String rawInput) {
        if (TextUtils.isEmpty(rawInput)) return activeCountryCode;
        int openParen = rawInput.indexOf('(');
        int closeParen = rawInput.indexOf(')');
        if (openParen > 0 && closeParen > openParen) {
            String codeInParen = rawInput.substring(openParen + 1, closeParen).trim();
            if (codeInParen.length() == 3) return codeInParen.toUpperCase(Locale.US);
        }
        String cleanKey = rawInput.replaceAll("[^a-zA-Z0-9 ]", "").trim().toUpperCase(Locale.US);
        String mapCode = countryCodeMap.get(cleanKey);
        return (mapCode != null) ? mapCode : rawInput.trim();
    }

    private String resolveCountryNameFromInput(String rawInput) {
        if (TextUtils.isEmpty(rawInput)) return activeCountryName;
        int openParen = rawInput.indexOf('(');
        if (openParen > 0) {
            return rawInput.substring(0, openParen).trim();
        }
        return rawInput.trim();
    }

    private void showAiChatDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ai_chat, null);

        View aiChatRoot = dialogView.findViewById(R.id.aiChatRoot);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseAiChat);
        TextView tvAiSubtitle = dialogView.findViewById(R.id.tvAiAssistantSubtitle);
        TextView tvContextDetails = dialogView.findViewById(R.id.tvAiContextDetails);
        View cardWelcome = dialogView.findViewById(R.id.cardAiWelcome);
        TextView chipSuggest1 = dialogView.findViewById(R.id.chipSuggestedQuestion1);
        TextView chipSuggest2 = dialogView.findViewById(R.id.chipSuggestedQuestion2);
        TextView chipSuggest3 = dialogView.findViewById(R.id.chipSuggestedQuestion3);
        ScrollView svChatScroll = dialogView.findViewById(R.id.svChatScroll);
        LinearLayout llChatContainer = dialogView.findViewById(R.id.llChatContainer);
        View llThinking = dialogView.findViewById(R.id.llAiThinkingIndicator);
        EditText etInput = dialogView.findViewById(R.id.etAiInputMessage);
        ImageButton btnSend = dialogView.findViewById(R.id.btnSendAiMessage);

        // Apply System Bar and IME Soft Keyboard Insets
        ViewCompat.setOnApplyWindowInsetsListener(aiChatRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    Math.max(systemBars.bottom, imeInsets.bottom)
            );
            svChatScroll.post(() -> svChatScroll.fullScroll(View.FOCUS_DOWN));
            return WindowInsetsCompat.CONSUMED;
        });

        // Populate Active Country Context Card
        String countryContext = String.format(Locale.US,
                "Country: %s (%s), Population: %s, GDP: %s, Data Year: %s",
                activeCountryName, activeCountryCode, currentPopulationText, currentGdpText, activeDataYear
        );

        tvContextDetails.setText(String.format(Locale.US, "%s (%s) • Pop: %s | GDP: %s • Year: %s",
                activeCountryName, activeCountryCode, currentPopulationText, currentGdpText, activeDataYear));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_GlobalInsights_FullScreenDialog)
                .setView(dialogView)
                .create();

        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Helper to send query and toggle welcome card GONE
        Runnable1<String> sendQueryAction = (queryText) -> {
            if (TextUtils.isEmpty(queryText)) {
                Toast.makeText(this, "Please enter a message or question.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (cardWelcome.getVisibility() == View.VISIBLE) {
                cardWelcome.setVisibility(View.GONE);
            }

            // Add User Message Bubble (plain text)
            addChatMessageBubble(llChatContainer, svChatScroll, true, queryText);
            etInput.setText("");
            llThinking.setVisibility(View.VISIBLE);

            geminiChatManager.sendMessage(queryText, countryContext, new GeminiChatManager.GeminiCallback() {
                @Override
                public void onSuccess(String responseText, String activeModelId) {
                    llThinking.setVisibility(View.GONE);

                    // Dynamically update chatbot header subtitle with exact active model
                    if (tvAiSubtitle != null && activeModelId != null) {
                        String modelDisplayName = GeminiChatManager.formatModelDisplayName(activeModelId);
                        tvAiSubtitle.setText(String.format(Locale.US, "Powered by %s", modelDisplayName));
                    }

                    // Render AI Message Bubble with Markwon Markdown
                    addChatMessageBubble(llChatContainer, svChatScroll, false, responseText);
                }

                @Override
                public void onError(String errorMessage) {
                    llThinking.setVisibility(View.GONE);
                    addChatMessageBubble(llChatContainer, svChatScroll, false, errorMessage);
                }
            });
        };

        btnSend.setOnClickListener(v -> sendQueryAction.accept(etInput.getText().toString().trim()));

        // Suggested Question Chips Click Handlers
        chipSuggest1.setOnClickListener(v -> sendQueryAction.accept(getString(R.string.ai_suggestion_1)));
        chipSuggest2.setOnClickListener(v -> sendQueryAction.accept(getString(R.string.ai_suggestion_2)));
        chipSuggest3.setOnClickListener(v -> sendQueryAction.accept(getString(R.string.ai_suggestion_3)));

        dialog.show();
    }

    private void addChatMessageBubble(LinearLayout container, ScrollView scroll, boolean isUser, String message) {
        LinearLayout cardWrapper = new LinearLayout(this);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(0, 0, 0, 16);
        cardWrapper.setLayoutParams(wrapperParams);
        cardWrapper.setGravity(isUser ? Gravity.END : Gravity.START);
        cardWrapper.setOrientation(LinearLayout.HORIZONTAL);

        if (!isUser) {
            // AI Avatar Circle
            ImageView aiAvatar = new ImageView(this);
            int avatarSize = (int) (32 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
            avatarParams.setMargins(0, 4, 10, 0);
            aiAvatar.setLayoutParams(avatarParams);
            aiAvatar.setImageResource(R.drawable.ic_globe);
            aiAvatar.setBackgroundResource(R.drawable.bg_pill_tag);
            aiAvatar.setPadding(12, 12, 12, 12);
            aiAvatar.setColorFilter(ContextCompat.getColor(this, R.color.primary));
            cardWrapper.addView(aiAvatar);
        }

        LinearLayout bubbleLayout = new LinearLayout(this);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82);
        bubbleLayout.setLayoutParams(bubbleParams);
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);
        bubbleLayout.setPadding(32, 24, 32, 24);

        if (isUser) {
            bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_user);
        } else {
            bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_ai);
        }

        TextView tvSender = new TextView(this);
        tvSender.setText(isUser ? "You" : "Global Insights AI");
        tvSender.setTextSize(11f);
        tvSender.setTypeface(null, Typeface.BOLD);
        tvSender.setTextColor(ContextCompat.getColor(this, isUser ? R.color.white : R.color.accent));

        TextView tvBody = new TextView(this);
        tvBody.setTextSize(14f);
        tvBody.setLineSpacing(6f, 1f);
        tvBody.setTextColor(ContextCompat.getColor(this, isUser ? R.color.white : R.color.text_primary));
        tvBody.setPadding(0, 6, 0, 0);

        if (isUser) {
            tvBody.setText(message);
        } else {
            markwon.setMarkdown(tvBody, message);
        }

        bubbleLayout.addView(tvSender);
        bubbleLayout.addView(tvBody);
        cardWrapper.addView(bubbleLayout);

        container.addView(cardWrapper);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showPreferencesDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_preferences, null);

        RadioGroup rgTheme = dialogView.findViewById(R.id.rgTheme);
        RadioButton rbLight = dialogView.findViewById(R.id.rbLight);
        RadioButton rbDark = dialogView.findViewById(R.id.rbDark);
        RadioButton rbSystem = dialogView.findViewById(R.id.rbSystem);
        AutoCompleteTextView spinnerPreferredCountry = dialogView.findViewById(R.id.spinnerPreferredCountry);
        View btnCancel = dialogView.findViewById(R.id.btnCancelPreferences);
        View btnSave = dialogView.findViewById(R.id.btnSavePreferences);

        // 1. Set current theme radio selection
        int currentMode = preferencesManager.getNightMode();
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            rbLight.setChecked(true);
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            rbDark.setChecked(true);
        } else {
            rbSystem.setChecked(true);
        }

        // 2. Populate AutoCompleteTextView with all available World Bank countries
        if (!allCountriesList.isEmpty()) {
            List<String> displayList = new ArrayList<>();
            for (WorldBankCountry c : allCountriesList) {
                displayList.add(c.name + " (" + c.id + ")");
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    displayList
            );
            spinnerPreferredCountry.setAdapter(adapter);
        }

        // Pre-fill current preferred country
        String prefCode = preferencesManager.getPreferredCountryCode();
        String prefName = preferencesManager.getPreferredCountryName();
        spinnerPreferredCountry.setText(String.format(Locale.US, "%s (%s)", prefName, prefCode), false);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) activeDialog = null;
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            // Save Theme Mode
            int selectedMode;
            int checkedId = rgTheme.getCheckedRadioButtonId();
            if (checkedId == R.id.rbLight) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rbDark) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }

            preferencesManager.setNightMode(selectedMode);

            // Save Preferred Country
            String rawCountryInput = spinnerPreferredCountry.getText().toString().trim();
            if (!TextUtils.isEmpty(rawCountryInput)) {
                String resolvedCode = activeCountryCode;
                String resolvedName = activeCountryName;

                int openParen = rawCountryInput.indexOf('(');
                int closeParen = rawCountryInput.indexOf(')');
                if (openParen > 0 && closeParen > openParen) {
                    resolvedCode = rawCountryInput.substring(openParen + 1, closeParen).trim();
                    resolvedName = rawCountryInput.substring(0, openParen).trim();
                } else {
                    String cleanKey = rawCountryInput.toUpperCase(Locale.US);
                    String mapCode = countryCodeMap.get(cleanKey);
                    if (mapCode != null) {
                        resolvedCode = mapCode;
                        resolvedName = rawCountryInput;
                    }
                }

                preferencesManager.setPreferredCountry(resolvedCode, resolvedName);

                if (!resolvedCode.equalsIgnoreCase(activeCountryCode)) {
                    etSearchCountry.setText(resolvedName);
                    highlightChipForCode(resolvedCode);
                    fetchWorldBankData(resolvedCode, resolvedName);
                }
            }

            AppCompatDelegate.setDefaultNightMode(selectedMode);
            dialog.dismiss();
            Toast.makeText(this, "Preferences & theme saved successfully!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void toggleFavoriteCountry() {
        if (TextUtils.isEmpty(currentPopulationText) || TextUtils.isEmpty(currentGdpText)) {
            Toast.makeText(this, "Please wait until country data finishes loading.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isFav = databaseHelper.isFavorite(activeCountryCode);
        if (isFav) {
            databaseHelper.removeFavorite(activeCountryCode);
            btnFavoriteCountry.setImageResource(R.drawable.ic_star_outline);
            Toast.makeText(this, "Removed " + activeCountryName + " from Favourites (SQLite)", Toast.LENGTH_SHORT).show();
        } else {
            databaseHelper.addFavorite(activeCountryName, activeCountryCode, currentPopulationText, currentGdpText);
            btnFavoriteCountry.setImageResource(R.drawable.ic_star);
            Toast.makeText(this, "Saved " + activeCountryName + " to Favourites (SQLite)", Toast.LENGTH_SHORT).show();
        }
        refreshFavoritesUI();
    }

    private void refreshFavoritesUI() {
        List<DatabaseHelper.FavoriteCountry> favorites = databaseHelper.getAllFavorites();
        if (favorites.isEmpty()) {
            tvNoFavorites.setVisibility(View.VISIBLE);
            hsvFavorites.setVisibility(View.GONE);
        } else {
            tvNoFavorites.setVisibility(View.GONE);
            hsvFavorites.setVisibility(View.VISIBLE);
            llFavoritesContainer.removeAllViews();

            for (DatabaseHelper.FavoriteCountry fav : favorites) {
                MaterialCardView card = new MaterialCardView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 0, 16, 0);
                card.setLayoutParams(params);
                card.setRadius(20f);
                card.setCardElevation(2f);
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_card));
                card.setStrokeColor(ContextCompat.getColor(this, R.color.border_color));
                card.setStrokeWidth(2);

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(28, 20, 28, 20);

                LinearLayout topRow = new LinearLayout(this);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvName = new TextView(this);
                tvName.setText(String.format(Locale.US, "%s (%s)", fav.name, fav.code));
                tvName.setTextSize(14f);
                tvName.setTypeface(null, Typeface.BOLD);
                tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

                ImageButton btnDelete = new ImageButton(this);
                btnDelete.setImageResource(R.drawable.ic_delete);
                btnDelete.setBackgroundResource(android.R.color.transparent);
                btnDelete.setPadding(12, 0, 0, 0);
                btnDelete.setContentDescription("Remove Favorite");
                btnDelete.setOnClickListener(v -> {
                    databaseHelper.removeFavorite(fav.code);
                    Toast.makeText(this, "Removed " + fav.name + " from Favourites", Toast.LENGTH_SHORT).show();
                    if (fav.code.equalsIgnoreCase(activeCountryCode)) {
                        btnFavoriteCountry.setImageResource(R.drawable.ic_star_outline);
                    }
                    refreshFavoritesUI();
                });

                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tvName.setLayoutParams(nameParams);

                topRow.addView(tvName);
                topRow.addView(btnDelete);

                TextView tvDetails = new TextView(this);
                tvDetails.setText(String.format(Locale.US, "Pop: %s\nGDP: %s", fav.population, fav.gdp));
                tvDetails.setTextSize(11f);
                tvDetails.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvDetails.setPadding(0, 4, 0, 0);

                layout.addView(topRow);
                layout.addView(tvDetails);
                card.addView(layout);

                card.setOnClickListener(v -> {
                    etSearchCountry.setText(fav.name);
                    highlightChipForCode(fav.code);
                    fetchWorldBankData(fav.code, fav.name);
                });

                llFavoritesContainer.addView(card);
            }
        }
    }

    private void saveCurrentCountryReport() {
        if (TextUtils.isEmpty(currentPopulationText) || TextUtils.isEmpty(currentGdpText)) {
            Toast.makeText(this, "Please wait until country data is loaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean saved = ReportStorageManager.saveReport(
                this,
                activeCountryName,
                activeCountryCode,
                currentPopulationText,
                currentGdpText,
                activeDataYear
        );

        if (saved) {
            refreshSavedReportsUI();

            // Dispatch internal app broadcast for report saved
            Intent broadcastIntent = new Intent(CountryBroadcastReceiver.ACTION_REPORT_SAVED);
            broadcastIntent.setPackage(getPackageName());
            broadcastIntent.putExtra("country_name", activeCountryName);
            broadcastIntent.putExtra("country_code", activeCountryCode);
            broadcastIntent.putExtra("population", currentPopulationText);
            broadcastIntent.putExtra("gdp", currentGdpText);
            sendBroadcast(broadcastIntent);
        } else {
            Toast.makeText(this, "Failed to save report.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshSavedReportsUI() {
        List<ReportStorageManager.CountryReport> reports = ReportStorageManager.getAllReports(this);
        if (reports.isEmpty()) {
            tvNoReports.setVisibility(View.VISIBLE);
            llReportsContainer.setVisibility(View.GONE);
        } else {
            tvNoReports.setVisibility(View.GONE);
            llReportsContainer.setVisibility(View.VISIBLE);
            llReportsContainer.removeAllViews();

            for (ReportStorageManager.CountryReport report : reports) {
                MaterialCardView card = new MaterialCardView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 0, 0, 16);
                card.setLayoutParams(params);
                card.setRadius(24f);
                card.setCardElevation(2f);
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_card));
                card.setStrokeColor(ContextCompat.getColor(this, R.color.border_color));
                card.setStrokeWidth(2);

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(32, 24, 32, 24);

                TextView tvHeader = new TextView(this);
                tvHeader.setText(String.format(Locale.US, "%s (%s) • Year: %s", report.countryName, report.countryCode, report.dataYear));
                tvHeader.setTextSize(15f);
                tvHeader.setTypeface(null, Typeface.BOLD);
                tvHeader.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

                TextView tvStats = new TextView(this);
                tvStats.setText(String.format(Locale.US, "Pop: %s\nGDP: %s\nSaved: %s", report.populationText, report.gdpText, report.savedAtDate));
                tvStats.setTextSize(12f);
                tvStats.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvStats.setPadding(0, 8, 0, 12);

                LinearLayout actionLayout = new LinearLayout(this);
                actionLayout.setOrientation(LinearLayout.HORIZONTAL);

                MaterialButton btnLoad = new MaterialButton(this);
                btnLoad.setText(getString(R.string.btn_view_live));
                btnLoad.setTextSize(12f);
                btnLoad.setCornerRadius(16);
                btnLoad.setOnClickListener(v -> {
                    etSearchCountry.setText(report.countryName);
                    highlightChipForCode(report.countryCode);
                    fetchWorldBankData(report.countryCode, report.countryName);
                });

                MaterialButton btnDelete = new MaterialButton(this);
                btnDelete.setText(getString(R.string.btn_delete));
                btnDelete.setTextSize(12f);
                btnDelete.setCornerRadius(16);
                btnDelete.setBackgroundColor(ContextCompat.getColor(this, R.color.border_color));
                btnDelete.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                btnDelete.setOnClickListener(v -> {
                    ReportStorageManager.deleteReport(this, report.id);
                    Toast.makeText(this, "Deleted report for " + report.countryName, Toast.LENGTH_SHORT).show();
                    refreshSavedReportsUI();
                });

                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                btnParams.setMargins(0, 0, 12, 0);
                btnLoad.setLayoutParams(btnParams);
                btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                actionLayout.addView(btnLoad);
                actionLayout.addView(btnDelete);

                layout.addView(tvHeader);
                layout.addView(tvStats);
                layout.addView(actionLayout);
                card.addView(layout);

                llReportsContainer.addView(card);
            }
        }
    }

    private void initCountryMap() {
        countryCodeMap.put("INDIA", "IND");
        countryCodeMap.put("IND", "IND");

        countryCodeMap.put("PAKISTAN", "PAK");
        countryCodeMap.put("PAK", "PAK");

        countryCodeMap.put("BANGLADESH", "BGD");
        countryCodeMap.put("BGD", "BGD");

        countryCodeMap.put("CHINA", "CHN");
        countryCodeMap.put("CHN", "CHN");

        countryCodeMap.put("USA", "USA");
        countryCodeMap.put("UNITED STATES", "USA");
        countryCodeMap.put("UNITED STATES OF AMERICA", "USA");
        countryCodeMap.put("US", "USA");
        countryCodeMap.put("AMERICA", "USA");

        countryCodeMap.put("UK", "GBR");
        countryCodeMap.put("UNITED KINGDOM", "GBR");
        countryCodeMap.put("GBR", "GBR");
        countryCodeMap.put("BRITAIN", "GBR");
        countryCodeMap.put("GREAT BRITAIN", "GBR");

        countryCodeMap.put("GERMANY", "DEU");
        countryCodeMap.put("DEU", "DEU");

        countryCodeMap.put("JAPAN", "JPN");
        countryCodeMap.put("JPN", "JPN");

        countryCodeMap.put("FRANCE", "FRA");
        countryCodeMap.put("FRA", "FRA");

        countryCodeMap.put("BRAZIL", "BRA");
        countryCodeMap.put("BRA", "BRA");

        countryCodeMap.put("CANADA", "CAN");
        countryCodeMap.put("CAN", "CAN");

        countryCodeMap.put("AUSTRALIA", "AUS");
        countryCodeMap.put("AUS", "AUS");
    }

    private void setupChipListeners(TextView chip, String name, String code) {
        chip.setOnClickListener(v -> {
            highlightChip(chip);
            etSearchCountry.setText(name);
            fetchWorldBankData(code, name);
        });
    }

    private void highlightChip(TextView selectedChip) {
        for (TextView c : countryChips) {
            c.setBackgroundResource(R.drawable.bg_chip_unselected);
            c.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
        if (selectedChip != null) {
            selectedChip.setBackgroundResource(R.drawable.bg_chip_selected);
            selectedChip.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }

    private void highlightChipForCode(String code) {
        if ("IND".equals(code)) {
            highlightChip(findViewById(R.id.chipIndia));
        } else if ("USA".equals(code)) {
            highlightChip(findViewById(R.id.chipUSA));
        } else if ("GBR".equals(code)) {
            highlightChip(findViewById(R.id.chipUK));
        } else if ("CHN".equals(code)) {
            highlightChip(findViewById(R.id.chipChina));
        } else if ("DEU".equals(code)) {
            highlightChip(findViewById(R.id.chipGermany));
        } else if ("JPN".equals(code)) {
            highlightChip(findViewById(R.id.chipJapan));
        } else if ("FRA".equals(code)) {
            highlightChip(findViewById(R.id.chipFrance));
        } else if ("BRA".equals(code)) {
            highlightChip(findViewById(R.id.chipBrazil));
        } else {
            highlightChip(null);
        }
    }

    private void handleCountryQuery(String rawQuery) {
        if (TextUtils.isEmpty(rawQuery)) return;

        Log.d(TAG, "Raw country query: " + rawQuery);

        String query = rawQuery.trim();

        // 1. Extract 3-letter ISO code if format is "Country Name (CODE)"
        int openParen = query.indexOf('(');
        int closeParen = query.indexOf(')');
        if (openParen > 0 && closeParen > openParen) {
            String codeInParen = query.substring(openParen + 1, closeParen).trim();
            if (codeInParen.length() == 3) {
                String extractedName = query.substring(0, openParen).trim();
                Log.d(TAG, "Extracted code from parens: " + codeInParen + " for name: " + extractedName);
                highlightChipForCode(codeInParen.toUpperCase(Locale.US));
                fetchWorldBankData(codeInParen.toUpperCase(Locale.US), extractedName);
                return;
            }
            query = query.substring(0, openParen).trim();
        }

        // 2. Clean query for text matching
        String cleanQuery = query.replaceAll("[^a-zA-Z0-9 ]", "").trim().toUpperCase(Locale.US);

        if (cleanQuery.startsWith("SEARCH FOR ")) {
            cleanQuery = cleanQuery.substring(11).trim();
        } else if (cleanQuery.startsWith("SEARCH ")) {
            cleanQuery = cleanQuery.substring(7).trim();
        } else if (cleanQuery.startsWith("FIND ")) {
            cleanQuery = cleanQuery.substring(5).trim();
        } else if (cleanQuery.startsWith("SHOW ")) {
            cleanQuery = cleanQuery.substring(5).trim();
        }

        // 3. Exact match in countryCodeMap
        String targetCode = countryCodeMap.get(cleanQuery);
        String targetName = query;

        // 4. Exact match in allCountriesList by name or ISO id
        if (targetCode == null && !allCountriesList.isEmpty()) {
            for (WorldBankCountry c : allCountriesList) {
                if (c.name.equalsIgnoreCase(cleanQuery) || c.id.equalsIgnoreCase(cleanQuery)) {
                    targetCode = c.id;
                    targetName = c.name;
                    break;
                }
            }
        }

        // 5. Partial substring match in allCountriesList
        if (targetCode == null && !allCountriesList.isEmpty()) {
            for (WorldBankCountry c : allCountriesList) {
                String cNameUpper = c.name.toUpperCase(Locale.US);
                String cIdUpper = c.id.toUpperCase(Locale.US);
                if (cNameUpper.contains(cleanQuery) || cIdUpper.contains(cleanQuery)) {
                    targetCode = c.id;
                    targetName = c.name;
                    break;
                }
            }
        }

        // 6. Direct 3-letter ISO code fallback
        if (targetCode == null && cleanQuery.length() == 3) {
            targetCode = cleanQuery;
        }

        if (targetCode != null) {
            Log.d(TAG, "Resolved " + rawQuery + " -> Code: " + targetCode + ", Name: " + targetName);
            highlightChipForCode(targetCode);
            fetchWorldBankData(targetCode, targetName);
        } else {
            Log.w(TAG, "Country resolution failed for query: " + rawQuery + " (Cleaned: " + cleanQuery + ")");
            Toast.makeText(this, getString(R.string.error_country_not_found), Toast.LENGTH_LONG).show();
        }
    }

    private void fetchWorldBankData(String code, String displayName) {
        activeCountryCode = code;
        activeCountryName = displayName;

        Log.d(TAG, "Fetching World Bank stats for Code: " + code + ", DisplayName: " + displayName);

        // Save as user's preferred country (SharedPreferences)
        preferencesManager.setPreferredCountry(code, displayName);

        // Update Favorite Button Icon
        if (databaseHelper.isFavorite(code)) {
            btnFavoriteCountry.setImageResource(R.drawable.ic_star);
        } else {
            btnFavoriteCountry.setImageResource(R.drawable.ic_star_outline);
        }

        progressBarLoading.setVisibility(View.VISIBLE);
        cardCountryAnalytics.setVisibility(View.GONE);

        executorService.execute(() -> {
            String popUrl = "https://api.worldbank.org/v2/country/" + code + "/indicator/SP.POP.TOTL?format=json&mrnev=1";
            String gdpUrl = "https://api.worldbank.org/v2/country/" + code + "/indicator/NY.GDP.MKTP.CD?format=json&mrnev=1";

            String popJson = makeHttpGetRequest(popUrl);
            String gdpJson = makeHttpGetRequest(gdpUrl);

            IndicatorResult popResult = parseWorldBankJson(popJson);
            IndicatorResult gdpResult = parseWorldBankJson(gdpJson);

            mainHandler.post(() -> {
                progressBarLoading.setVisibility(View.GONE);

                if (popJson == null && gdpJson == null) {
                    Log.e(TAG, "Network error fetching World Bank indicators for " + code);
                    Toast.makeText(DashboardActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    return;
                }

                String finalCountryName = !popResult.countryName.isEmpty() ? popResult.countryName : displayName;
                activeCountryName = finalCountryName;
                activeDataYear = !popResult.dateYear.equals("N/A") ? popResult.dateYear : gdpResult.dateYear;

                currentPopulationText = formatPopulation(popResult.value);
                currentGdpText = formatGdp(gdpResult.value);

                Log.d(TAG, "Successfully loaded " + finalCountryName + " (" + code + ") - Pop: " + currentPopulationText + ", GDP: " + currentGdpText);

                tvCountryName.setText(finalCountryName);
                tvCountryCode.setText(code);
                tvDataYear.setText(String.format(Locale.US, "Data Year: %s", activeDataYear));
                tvPopulation.setText(currentPopulationText);
                tvGdp.setText(currentGdpText);

                cardCountryAnalytics.setVisibility(View.VISIBLE);
            });
        });
    }

    private void checkAudioPermissionAndSearch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchVoiceSearch();
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void launchVoiceSearch() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is not available on this device or emulator.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a country name (e.g. India, Pakistan, USA, Japan)...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        try {
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            startDirectSpeechRecognizer();
        }
    }

    private void startDirectSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is not available on this device or emulator.", Toast.LENGTH_LONG).show();
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Toast.makeText(DashboardActivity.this, "Listening... Speak country name now", Toast.LENGTH_SHORT).show();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                String errorMsg = getSpeechErrorMessage(error);
                Toast.makeText(DashboardActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenQuery = matches.get(0);
                    etSearchCountry.setText(spokenQuery);
                    handleCountryQuery(spokenQuery);
                    Toast.makeText(DashboardActivity.this, "Recognized: " + spokenQuery, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DashboardActivity.this, "No speech matched. Please try speaking clearly.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private String getSpeechErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error. Please check your microphone.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Speech client error. Please try again.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission is required for voice search.";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error. Voice search requires internet access.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout. Please check your internet connection.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No country name recognized. Please speak clearly (e.g. India, USA, Pakistan, China).";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Voice search is busy. Please wait a moment.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Speech recognition server error. Please try again.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech detected. Please speak into the microphone.";
            default:
                return "Voice recognition failed (code " + error + "). Please try again.";
        }
    }

    private void speakCountryInsights() {
        if (!isTtsReady) {
            Toast.makeText(this, "Text-to-Speech engine is initializing...", Toast.LENGTH_SHORT).show();
            return;
        }

        String speechText = String.format(Locale.US,
                "%s has a total population of %s, and a Gross Domestic Product of %s, according to the latest World Bank metrics.",
                tvCountryName.getText().toString(),
                currentPopulationText,
                currentGdpText
        );

        textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "Utterance_GlobalInsights");
    }

    private static class IndicatorResult {
        boolean success = false;
        String countryName = "";
        String dateYear = "N/A";
        double value = -1;
    }

    private IndicatorResult parseWorldBankJson(String jsonResponse) {
        IndicatorResult result = new IndicatorResult();
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray rootArray = new JSONArray(jsonResponse);
            if (rootArray.length() >= 2 && !rootArray.isNull(1)) {
                JSONArray dataArray = rootArray.getJSONArray(1);
                if (dataArray.length() > 0 && !dataArray.isNull(0)) {
                    JSONObject dataObj = dataArray.getJSONObject(0);
                    result.dateYear = dataObj.optString("date", "N/A");

                    JSONObject countryObj = dataObj.optJSONObject("country");
                    if (countryObj != null) {
                        result.countryName = countryObj.optString("value", "");
                    }

                    if (!dataObj.isNull("value")) {
                        result.value = dataObj.getDouble("value");
                        result.success = true;
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    private String formatPopulation(double value) {
        if (value < 0) return "Data Not Available";
        long count = Math.round(value);
        return NumberFormat.getInstance(Locale.US).format(count);
    }

    private String formatGdp(double value) {
        if (value < 0) return "Data Not Available";
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        currencyFormat.setMaximumFractionDigits(0);
        String rawFormatted = currencyFormat.format(value);

        String shorthand = "";
        if (value >= 1e12) {
            shorthand = String.format(Locale.US, " (~$%.2f Trillion)", value / 1e12);
        } else if (value >= 1e9) {
            shorthand = String.format(Locale.US, " (~$%.2f Billion)", value / 1e9);
        } else if (value >= 1e6) {
            shorthand = String.format(Locale.US, " (~$%.2f Million)", value / 1e6);
        }
        return rawFormatted + shorthand;
    }

    private String makeHttpGetRequest(String urlString) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                StringBuilder builder = new StringBuilder();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append("\n");
                }
                return builder.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        if (geminiChatManager != null) {
            geminiChatManager.shutdown();
        }
        executorService.shutdown();
    }
}