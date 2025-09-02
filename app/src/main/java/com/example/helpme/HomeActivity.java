package com.example.helpme;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authListener;
    private FirebaseFirestore db;

    // UI (shared)
    private WebView mapView;
    private ImageView menuBtn;
    private ImageView backBtn;
    private TextView helperTag;
    private TextView locationText;

    // User actions
    private ImageView btnSavedList;
    private ImageView btnSaveLoc;
    private ImageView btnMyLoc;
    private ImageView btnOrder;

    // Drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    // Search panel (user)
    private View searchPanel;
    private EditText searchBox;
    private RecyclerView suggestionsList;

    // Root containers for role
    private View userRoot;
    private View helperRoot;
    private RecyclerView helperRecycler;

    // Role
    private String role = "user";

    // Location
    private FusedLocationProviderClient fused;
    private static final double FALLBACK_LAT = 31.9539;
    private static final double FALLBACK_LNG = 35.9106;

    private String currentAddress = "ADD YOUR LOCATION";
    private Double currentLat = null;
    private Double currentLng = null;

    // map state
    private boolean mapInitialized = false;

    // legacy location listener
    private LocationListener legacyOnceListener;

    // Suggestions
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private final ArrayList<Suggestion> suggestions = new ArrayList<>();
    private SuggestionsAdapter adapter;

    // Saved locations
    private static final String PREFS = "saved_locations";
    private static final String KEY_SET = "items";

    private final ActivityResultLauncher<String[]> locationPermsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false));
                boolean coarse = Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false));
                if (fine || coarse) {
                    fetchLocation();
                } else {
                    loadMapByLatLng(FALLBACK_LAT, FALLBACK_LNG);
                    Toast.makeText(this, "Location permission denied. Showing default map.", Toast.LENGTH_SHORT).show();
                }
            });

    private AlertDialog savedDialog;

    // ===== User order flow state =====
    private Dialog orderDialog;
    private TextView orderTitle;
    private ImageView orderCenterIcon;
    private TextView orderHelperName;
    private TextView orderProfession;
    private LinearLayout orderStarsRow;
    private ImageView orderCallBtn;
    private ImageView orderCloseBtn;
    private String activeOrderId = null;
    private ListenerRegistration orderListener = null;
    private String acceptedPhone = null;

    // Keep a handle to the rating dialog so we can always dismiss it
    private Dialog feedbackDialog;

    // Keep user’s active order synced
    private ListenerRegistration userActiveOrderReg = null;

    // ===== Helper side =====
    private HelperOrdersAdapter helperAdapter;
    private ListenerRegistration helperOrdersSub;

    // ===== Helper live tracking (journey) =====
    private boolean helperTracking = false;
    private LocationCallback helperLocCb = null;

    // ===== visual/back + one-time rating =====
    private static final int OVERLAY_BG_COLOR = Color.parseColor("#99000000"); // black, slightly transparent
    private Dialog helperAcceptDialog;
    private Dialog navDialog;
    private String overlaySuppressedOrderId = null;   // if user hides overlay with back
    private String overlaySuppressedStatus  = null;
    private String lastOrderStatusShown     = null;

    // show rating only once per order id
    private String ratingShownForOrderId = null;

    // ===== helper list live distance =====
    private boolean helperListLocUpdates = false;
    private LocationCallback helperListLocCb = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initializeViews();
        setupSearch();
        wireDrawer();
        wireClicks();
        wireIconClicks();

        // Auth-driven wiring: attach/detach when user changes
        authListener = firebaseAuth -> {
            FirebaseUser u = firebaseAuth.getCurrentUser();
            if (u == null) {
                // signed out: clean up UI/listeners
                if (userActiveOrderReg != null) { userActiveOrderReg.remove(); userActiveOrderReg = null; }
                if (orderListener != null) { orderListener.remove(); orderListener = null; }
                dismissOrderOverlay();
            } else {
                // signed in: ensure profile/role then attach listeners
                loadProfile();
            }
        };
        auth.addAuthStateListener(authListener);

        applyRoleUI(); // default "user" until profile loads
        ensureLocation();
    }

    private void initializeViews() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fused = LocationServices.getFusedLocationProviderClient(this);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        mapView = findViewById(R.id.mapView);
        menuBtn = findViewById(R.id.menuBtn);
        backBtn = findViewById(R.id.backBtn);
        helperTag = findViewById(R.id.helperTag);
        locationText = findViewById(R.id.locationText);

        btnSavedList = findViewById(R.id.btnSavedList);
        btnSaveLoc = findViewById(R.id.btnSaveLoc);
        btnMyLoc = findViewById(R.id.btnMyLoc);
        btnOrder = findViewById(R.id.btnOrder);

        searchPanel = findViewById(R.id.searchPanel);
        searchBox = findViewById(R.id.searchBox);
        suggestionsList = findViewById(R.id.suggestionsList);

        userRoot = findViewById(R.id.userRoot);
        helperRoot = findViewById(R.id.helperRoot);
        helperRecycler = findViewById(R.id.helperRecycler);
        if (helperRecycler != null) helperRecycler.setLayoutManager(new LinearLayoutManager(this));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupMap() {
        if (mapView == null) return;

        mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        WebSettings ws = mapView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);

        final String html =
                "<!DOCTYPE html><html><head>"
                        + "<meta name='viewport' content='width=device-width, initial-scale=1.0'/>"
                        + "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>"
                        + "<style>html,body,#map{height:100%;margin:0} .lbl{background:#fff;padding:2px 6px;border-radius:4px;font:12px sans-serif}</style>"
                        + "</head><body>"
                        + "<div id='map'></div>"
                        + "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"
                        + "<script>"
                        + "var map, userMarker, helperMarker, routeLine;"
                        + "function init(lat,lng,z){"
                        + "  map=L.map('map',{zoomControl:true}).setView([lat,lng],z);"
                        + "  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map);"
                        + "  userMarker=L.marker([lat,lng]).addTo(map);"
                        + "  map.on('click',function(e){ if(window.Android&&Android.onPick){Android.onPick(e.latlng.lat,e.latlng.lng);} });"
                        + "}"
                        + "function showLoc(lat,lng,z){"
                        + "  if(!map){init(lat,lng,z||16);return;}"
                        + "  if(userMarker){map.removeLayer(userMarker);} "
                        + "  userMarker=L.marker([lat,lng]).addTo(map);"
                        + "  try{ map.setView([lat,lng], z || map.getZoom() || 16, {animate:true}); }catch(e){}"
                        + "}"
                        + "function updHelper(hLat,hLng,uLat,uLng){"
                        + "  if(!map){init(uLat,uLng,16);} "
                        + "  if(helperMarker){map.removeLayer(helperMarker);} "
                        + "  helperMarker=L.marker([hLat,hLng]).addTo(map);"
                        + "  if(routeLine){map.removeLayer(routeLine);} "
                        + "  routeLine=L.polyline([[hLat,hLng],[uLat,uLng]],{color:'#ffcc00',dashArray:'6,6',weight:4}).addTo(map);"
                        + "  var b=routeLine.getBounds(); map.fitBounds(b,{padding:[30,30]});"
                        + "}"
                        + "</script></body></html>";

        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.addJavascriptInterface(new MapBridge(), "Android");
        mapView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                evaluateJS("init(" + FALLBACK_LAT + "," + FALLBACK_LNG + ",16)");
                mapInitialized = true;
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                try {
                    ViewGroup parent = (ViewGroup) view.getParent();
                    if (parent != null) {
                        int index = parent.indexOfChild(view);
                        ViewGroup.LayoutParams lp = view.getLayoutParams();
                        parent.removeView(view);
                        try { view.destroy(); } catch (Throwable ignore) {}
                        mapView = new WebView(HomeActivity.this);
                        mapView.setId(R.id.mapView);
                        parent.addView(mapView, index, lp);
                    } else {
                        try { view.destroy(); } catch (Throwable ignore) {}
                        mapView = findViewById(R.id.mapView);
                    }
                } catch (Throwable ignore) {}
                mapInitialized = false;
                setupMap();
                return true;
            }
        });

        mapView.loadDataWithBaseURL("https://unpkg.com", html, "text/html", "UTF-8", null);
    }

    private void teardownMap() {
        try {
            if (mapView != null) {
                try { mapView.removeJavascriptInterface("Android"); } catch (Throwable ignore) {}
                try { mapView.loadUrl("about:blank"); } catch (Throwable ignore) {}
                try { mapView.clearHistory(); } catch (Throwable ignore) {}
                try { mapView.clearCache(true); } catch (Throwable ignore) {}
                try { mapView.onPause(); } catch (Throwable ignore) {}
                try { mapView.destroy(); } catch (Throwable ignore) {}
                mapInitialized = false;
            }
        } catch (Throwable ignore) {}
    }

    private void setupSearch() {
        if (suggestionsList == null) return;
        adapter = new SuggestionsAdapter();
        suggestionsList.setLayoutManager(new LinearLayoutManager(this));
        suggestionsList.setAdapter(adapter);

        if (searchBox != null) {
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    debounce.removeCallbacksAndMessages(null);
                    final String q = s.toString();
                    debounce.postDelayed(() -> queryNominatim(q), 250);
                }
            });

            searchBox.setOnEditorActionListener((tv, actionId, event) -> {
                boolean enter = event != null &&
                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                        event.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                    performQuerySearch();
                    return true;
                }
                return false;
            });
        }
    }

    private void wireDrawer() {
        if (menuBtn != null) {
            menuBtn.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                drawerLayout.closeDrawers();

                if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    return true;
                } else if (id == R.id.nav_orders) {
                    Intent i = new Intent(this, OrdersActivity.class);
                    i.putExtra("role", role); // "user" or "helper"
                    startActivity(i);
                    return true;
                }


                else if (id == R.id.nav_logout) {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(this, AccountChooserActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                    return true;
                }
                return false;
            });
        }
    }

    private void wireClicks() {
        if (backBtn != null) backBtn.setOnClickListener(v -> onBackPressed());

        View locationBar = findViewById(R.id.locationBar);
        if (locationBar != null) {
            locationBar.setOnClickListener(v -> {
                if ("helper".equalsIgnoreCase(role)) {
                    Toast.makeText(this, "Helpers cannot change location", Toast.LENGTH_SHORT).show();
                    return;
                }
                toggleSearchPanel(true);
                if (searchBox != null) searchBox.requestFocus();
            });
        }

        if (drawerLayout != null) {
            drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override public void onDrawerOpened(View drawerView) { toggleSearchPanel(false); }
            });
        }
    }

    private void wireIconClicks() {
        if (btnSavedList != null) btnSavedList.setOnClickListener(v -> showSavedLocationsDialog());

        if (btnSaveLoc != null) btnSaveLoc.setOnClickListener(v -> {
            if (isDefaultLocationText()) {
                Toast.makeText(this, "Pick a location first", Toast.LENGTH_SHORT).show();
                return;
            }
            saveCurrentToPrefs();
            Toast.makeText(this, "Location saved", Toast.LENGTH_SHORT).show();
        });

        if (btnMyLoc != null) btnMyLoc.setOnClickListener(v -> {
            toggleSearchPanel(false);
            getMyLocationAndSet();
        });

        if (btnOrder != null) btnOrder.setOnClickListener(v -> {
            if (!"user".equalsIgnoreCase(role)) {
                Toast.makeText(this, "Only users can place orders", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentLat == null || currentLng == null) {
                Toast.makeText(this, "Getting your location… please try again in a moment.", Toast.LENGTH_SHORT).show();
                ensureLocation();
                return;
            }
            if (isDefaultLocationText() || TextUtils.isEmpty(currentAddress)) {
                currentAddress = String.format(Locale.US, "%.5f, %.5f", currentLat, currentLng);
                if (locationText != null) locationText.setText(currentAddress.toUpperCase(Locale.ROOT));
            }
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
                return;
            }
            saveLocationToFirestore(currentAddress, currentLat, currentLng);
            createAndWatchOrder();
        });
    }

    @Override
    public void onBackPressed() {
        if (searchPanel != null && searchPanel.getVisibility() == View.VISIBLE) {
            toggleSearchPanel(false); return;
        }
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START); return;
        }
        if (navDialog != null && navDialog.isShowing()) { navDialog.dismiss(); navDialog = null; return; }
        if (helperAcceptDialog != null && helperAcceptDialog.isShowing()) { helperAcceptDialog.dismiss(); helperAcceptDialog = null; return; }
        if (feedbackDialog != null && feedbackDialog.isShowing()) { feedbackDialog.dismiss(); feedbackDialog = null; return; }
        if (savedDialog != null && savedDialog.isShowing()) { savedDialog.dismiss(); savedDialog = null; return; }

        if (orderDialog != null && orderDialog.isShowing()) {
            overlaySuppressedOrderId = activeOrderId;
            overlaySuppressedStatus  = lastOrderStatusShown;
            dismissOrderOverlay();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        teardownMap();

        if (orderListener != null) { orderListener.remove(); orderListener = null; }
        if (helperOrdersSub != null) { helperOrdersSub.remove(); helperOrdersSub = null; }
        if (userActiveOrderReg != null) { userActiveOrderReg.remove(); userActiveOrderReg = null; }

        if (authListener != null && auth != null) auth.removeAuthStateListener(authListener);

        stopHelperLiveTracking();
        stopHelperListLocUpdates();

        if (legacyOnceListener != null) {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                lm.removeUpdates(legacyOnceListener);
            } catch (Throwable ignore) {}
            legacyOnceListener = null;
        }

        if (feedbackDialog != null && feedbackDialog.isShowing()) {
            try { feedbackDialog.dismiss(); } catch (Throwable ignore) {}
        }
        feedbackDialog = null;

        if (helperAcceptDialog != null && helperAcceptDialog.isShowing()) {
            try { helperAcceptDialog.dismiss(); } catch (Throwable ignore) {}
        }
        helperAcceptDialog = null;

        if (navDialog != null && navDialog.isShowing()) {
            try { navDialog.dismiss(); } catch (Throwable ignore) {}
        }
        navDialog = null;

        super.onDestroy();
    }

    private boolean isDefaultLocationText() {
        return locationText != null && "ADD YOUR LOCATION".contentEquals(locationText.getText());
    }

    private void applyRoleUI() {
        boolean isHelper = "helper".equalsIgnoreCase(role);

        if (helperTag != null) {
            helperTag.setText(isHelper ? "HELPER" : "USER");
            helperTag.setVisibility(View.VISIBLE);
        }

        if (userRoot != null) userRoot.setVisibility(isHelper ? View.GONE : View.VISIBLE);
        if (helperRoot != null) helperRoot.setVisibility(isHelper ? View.VISIBLE : View.GONE);

        if (searchPanel != null) searchPanel.setVisibility(isHelper ? View.GONE : View.VISIBLE);

        if (isHelper) {
            if (mapView != null) mapView.setVisibility(View.GONE);
            teardownMap();
            setupHelperOrdersList();
            startHelperListLocUpdates();   // live GPS for distances
        } else {
            stopHelperListLocUpdates();
            if (mapView != null) mapView.setVisibility(View.VISIBLE);
            if (!mapInitialized) setupMap();
        }

        attachUserActiveOrderListenerIfUser();
    }

    private void evaluateJS(String js) {
        if (mapView != null && mapInitialized) mapView.evaluateJavascript(js, null);
    }

    private void loadMapByLatLng(double lat, double lng) {
        evaluateJS("showLoc(" + lat + "," + lng + ",16)");
    }

    // ===== location bootstrap =====
    private void ensureLocation() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            fetchLocation();
        } else {
            locationPermsLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean isPlayServicesAvailable() {
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        return code == ConnectionResult.SUCCESS;
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation() {
        if (!isPlayServicesAvailable()) { fetchWithLocationManager(); return; }

        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(this::onLocation)
                .addOnFailureListener(e -> LocationServices.getFusedLocationProviderClient(this)
                        .getLastLocation()
                        .addOnSuccessListener(this::onLocation)
                        .addOnFailureListener(err -> fetchWithLocationManager()));
    }

    @SuppressLint({"MissingPermission"})
    private void fetchWithLocationManager() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(p)) {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                }
            }
            if (best != null) { onLocation(best); return; }

            legacyOnceListener = new LocationListener() {
                @Override public void onLocationChanged(Location l) {
                    onLocation(l);
                    try { lm.removeUpdates(this); } catch (Throwable ignore) {}
                    legacyOnceListener = null;
                }
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, legacyOnceListener, Looper.getMainLooper());
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, legacyOnceListener, Looper.getMainLooper());
        } catch (Throwable t) {
            loadMapByLatLng(FALLBACK_LAT, FALLBACK_LNG);
            Toast.makeText(this, "Couldn't get location. Showing default map.", Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocation(@Nullable Location loc) {
        if (loc != null) {
            currentLat = loc.getLatitude();
            currentLng = loc.getLongitude();
            if (!"helper".equalsIgnoreCase(role)) loadMapByLatLng(currentLat, currentLng);
            reverseGeocode(loc.getLatitude(), loc.getLongitude());
        } else {
            currentLat = FALLBACK_LAT;
            currentLng = FALLBACK_LNG;
            if (!"helper".equalsIgnoreCase(role)) loadMapByLatLng(FALLBACK_LAT, FALLBACK_LNG);
        }
    }

    private void reverseGeocode(double lat, double lng) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String addressLine = address.getAddressLine(0);
                    runOnUiThread(() -> { if (addressLine != null) setChosenLocation(addressLine, lat, lng, true); });
                } else {
                    runOnUiThread(() -> setChosenLocation(String.format(Locale.US, "%.5f, %.5f", lat, lng), lat, lng, true));
                }
            } catch (IOException e) {
                runOnUiThread(() -> setChosenLocation(String.format(Locale.US, "%.5f, %.5f", lat, lng), lat, lng, true));
            } finally {
                executor.shutdown();
            }
        });
    }

    private void loadProfile() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            startActivity(new Intent(this, AccountChooserActivity.class));
            finish();
            return;
        }
        db.collection("users").document(u.getUid()).get()
                .addOnSuccessListener(this::applyProfile)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Profile load failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void applyProfile(DocumentSnapshot snap) {
        if (snap != null && snap.exists() && snap.contains("role")) {
            role = String.valueOf(snap.get("role")).toLowerCase();
        }
        applyRoleUI();
    }

    // ===== search & geocode =====
    private void performQuerySearch() {
        if (searchBox == null) return;
        String q = searchBox.getText().toString().trim();
        if (TextUtils.isEmpty(q)) { Toast.makeText(this, "Type a place/address", Toast.LENGTH_SHORT).show(); return; }
        geocodeAndSave(q);
    }

    private void queryNominatim(String raw) {
        if (!isNetworkAvailable()) {
            runOnUiThread(() -> Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show());
            return;
        }

        final String q = raw == null ? "" : raw.trim();
        if (q.length() < 2) {
            suggestions.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ArrayList<Suggestion> result = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                String url = "https://nominatim.openstreetmap.org/search?format=json&limit=8&addressdetails=1&q="
                        + URLEncoder.encode(q, "UTF-8");
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "HelpMeApp/1.0");

                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String name = o.optString("display_name", "");
                    double lat = Double.parseDouble(o.optString("lat", "0"));
                    double lon = Double.parseDouble(o.optString("lon", "0"));
                    if (!name.isEmpty()) result.add(new Suggestion(name, lat, lon));
                }
            } catch (Exception ignore) {
            } finally { if (conn != null) conn.disconnect(); }

            runOnUiThread(() -> {
                suggestions.clear();
                suggestions.addAll(result);
                if (adapter != null) adapter.notifyDataSetChanged();
            });
            executor.shutdown();
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void geocodeAndSave(String q) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            double lat = Double.NaN, lng = Double.NaN;
            String addressOut = q;
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> list = geocoder.getFromLocationName(q, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    if (a.getLatitude() != 0 || a.getLongitude() != 0) {
                        lat = a.getLatitude();
                        lng = a.getLongitude();
                    }
                    if (a.getAddressLine(0) != null) addressOut = a.getAddressLine(0);
                }
            } catch (IOException ignored) {
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Geocoding failed", Toast.LENGTH_SHORT).show());
            }

            final String finalAddress = addressOut;
            final double finalLat = lat, finalLng = lng;
            runOnUiThread(() -> setChosenLocation(finalAddress, finalLat, finalLng, true));
            executor.shutdown();
        });
    }

    private void setChosenLocation(String address, double lat, double lng, boolean persistRemote) {
        currentAddress = address;
        currentLat = (Double.isNaN(lat) ? null : lat);
        currentLng = (Double.isNaN(lng) ? null : lng);

        if (locationText != null) locationText.setText(address.toUpperCase(Locale.ROOT));

        if (currentLat != null && currentLng != null && !"helper".equalsIgnoreCase(role)) {
            loadMapByLatLng(currentLat, currentLng);
        }
        if (persistRemote) saveLocationToFirestore(address, currentLat, currentLng);
    }

    private void saveLocationToFirestore(String address, Double lat, Double lng) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;

        HashMap<String, Object> loc = new HashMap<>();
        loc.put("address", address);
        loc.put("lat", lat);
        loc.put("lng", lng);

        db.collection("users").document(u.getUid())
                .update("location", loc)
                .addOnFailureListener(err ->
                        Toast.makeText(this, "Save failed: " + err.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void toggleSearchPanel(boolean show) {
        if (searchPanel == null) return;
        if ("helper".equalsIgnoreCase(role)) { searchPanel.setVisibility(View.GONE); return; }
        searchPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show && searchBox != null) {
            searchBox.setText("");
            suggestions.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    }

    // ===== saved locations =====
    private void saveCurrentToPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_SET, new HashSet<>()));
        try {
            JSONObject o = new JSONObject();
            o.put("title", currentAddress);
            if (currentLat != null) o.put("lat", currentLat);
            if (currentLng != null) o.put("lng", currentLng);
            set.add(o.toString());
            prefs.edit().putStringSet(KEY_SET, set).apply();
        } catch (JSONException ignored) {}
    }

    private ArrayList<Suggestion> loadSavedFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(KEY_SET, new HashSet<>());
        ArrayList<Suggestion> list = new ArrayList<>();
        for (String js : set) {
            try {
                JSONObject o = new JSONObject(js);
                String title = o.optString("title", "");
                double lat = o.has("lat") ? o.optDouble("lat") : Double.NaN;
                double lng = o.has("lng") ? o.optDouble("lng") : Double.NaN;
                if (!title.isEmpty()) list.add(new Suggestion(title, lat, lng));
            } catch (JSONException ignored) {}
        }
        return list;
    }

    private void writeSavedToPrefs(ArrayList<Suggestion> list) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> set = new HashSet<>();
        for (Suggestion s : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("title", s.title);
                if (!Double.isNaN(s.lat)) o.put("lat", s.lat);
                if (!Double.isNaN(s.lon)) o.put("lng", s.lon);
                set.add(o.toString());
            } catch (JSONException ignored) {}
        }
        prefs.edit().putStringSet(KEY_SET, set).apply();
    }

    private void showSavedLocationsDialog() {
        ArrayList<Suggestion> saved = loadSavedFromPrefs();
        if (saved.isEmpty()) {
            if (savedDialog != null && savedDialog.isShowing()) savedDialog.dismiss();
            Toast.makeText(this, "No saved locations", Toast.LENGTH_SHORT).show();
            return;
        }

        ListView listView = new ListView(this);
        listView.setDividerHeight(1);

        SavedListAdapter adapter = new SavedListAdapter(saved, new SavedListAdapter.OnChange() {
            @Override public void onSelect(Suggestion s) {
                setChosenLocation(s.title, s.lat, s.lon, true);
                toggleSearchPanel(false);
            }
            @Override public void onDelete(int position) {
                saved.remove(position);
                writeSavedToPrefs(saved);
                ((android.widget.BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
                if (saved.isEmpty() && savedDialog != null) savedDialog.dismiss();
            }
        });

        listView.setAdapter(adapter);
        savedDialog = new AlertDialog.Builder(this)
                .setTitle("Saved locations")
                .setView(listView)
                .setNegativeButton("Close", null)
                .create();
        savedDialog.show();

        // dark transparent background feel
        if (savedDialog.getWindow() != null) {
            savedDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            savedDialog.getWindow().setBackgroundDrawable(new ColorDrawable(OVERLAY_BG_COLOR));
        }
    }

    // ===== current GPS quick-set =====
    @SuppressLint("MissingPermission")
    private void getMyLocationAndSet() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locationPermsLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        fetchLocation();
    }

    // ===== USER: order flow =====

    private void resetOrderStateForNewRequest() {
        if (feedbackDialog != null) {
            try { if (feedbackDialog.isShowing()) feedbackDialog.dismiss(); } catch (Throwable ignore) {}
            feedbackDialog = null;
        }
        if (orderDialog != null) {
            try { if (orderDialog.isShowing()) orderDialog.dismiss(); } catch (Throwable ignore) {}
        }
        orderDialog = null;
        orderTitle = null; orderCenterIcon = null; orderHelperName = null;
        orderProfession = null; orderStarsRow = null; orderCallBtn = null; orderCloseBtn = null;

        if (orderListener != null) { orderListener.remove(); orderListener = null; }
        activeOrderId = null;
        acceptedPhone = null;
    }

    private void createAndWatchOrder() {
        resetOrderStateForNewRequest();

        FirebaseUser u = auth.getCurrentUser();
        if (u == null) { Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show(); return; }
        if (activeOrderId != null) { return; }

        db.collection("users").document(u.getUid()).get()
                .addOnSuccessListener(profile -> {
                    String userName = deriveUserName(profile, u);

                    HashMap<String, Object> order = new HashMap<>();
                    order.put("status", "searching");
                    order.put("userId", u.getUid());
                    order.put("userName", userName);
                    order.put("address", currentAddress);
                    order.put("lat", currentLat);
                    order.put("lng", currentLng);
                    order.put("deniedBy", new ArrayList<String>());
                    order.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("orders").add(order)
                            .addOnSuccessListener(ref -> {
                                activeOrderId = ref.getId();
                                showOrderOverlaySearching();

                                orderListener = ref.addSnapshotListener((snap, err) -> {
                                    if (err != null || snap == null || !snap.exists()) return;
                                    String st = snap.getString("status");
                                    if ("accepted".equalsIgnoreCase(st)) {
                                        String helperName = snap.getString("helperName");
                                        String profession = snap.getString("helperProfession");
                                        Double ratingAvg = getHelperRatingFromOrder(snap); // numeric
                                        acceptedPhone = snap.getString("helperPhone");
                                        updateOrderOverlayAccepted(
                                                helperName == null ? "YOUR HELPER" : helperName,
                                                profession == null ? "" : profession,
                                                ratingAvg
                                        );
                                    } else if ("in_progress".equalsIgnoreCase(st)) {
                                        updateOrderOverlayInProgress();
                                    } else if ("completed".equalsIgnoreCase(st)) {
                                        if (snap.get("userRating") == null) showFeedbackDialog(snap);
                                        else { dismissOrderOverlay(); }
                                    }

                                    Object locObj = snap.get("helperLoc");
                                    if (locObj instanceof java.util.Map && currentLat != null && currentLng != null) {
                                        Double hLat = toDouble(((java.util.Map<?, ?>) locObj).get("lat"));
                                        Double hLng = toDouble(((java.util.Map<?, ?>) locObj).get("lng"));
                                        if (hLat != null && hLng != null) {
                                            evaluateJS("updHelper(" + hLat + "," + hLng + "," + currentLat + "," + currentLng + ")");
                                        }
                                    }
                                });
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Order failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Profile read failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showOrderOverlaySearching() {
        lastOrderStatusShown = "searching";
        if (orderDialog != null && orderDialog.isShowing()) {
            if (orderTitle != null) orderTitle.setText("LOOKING FOR A NEARBY HELPER");
            if (orderCenterIcon != null) {
                int pin = getResources().getIdentifier("location", "drawable", getPackageName());
                orderCenterIcon.setImageResource(pin == 0 ? android.R.drawable.ic_dialog_map : pin);
            }
            if (orderHelperName != null) orderHelperName.setVisibility(View.GONE);
            if (orderProfession != null) orderProfession.setVisibility(View.GONE);
            if (orderStarsRow != null) orderStarsRow.setVisibility(View.GONE);
            if (orderCallBtn != null) orderCallBtn.setVisibility(View.GONE);
            if (orderCloseBtn != null) {
                orderCloseBtn.setVisibility(View.VISIBLE);
                orderCloseBtn.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setMessage("Cancel this request?")
                            .setPositiveButton("Yes", (d, wBtn) -> cancelActiveOrderIfSearching())
                            .setNegativeButton("No", null).show();
                });
            }
            return;
        }

        orderDialog = new Dialog(this);
        orderDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        orderDialog.setCancelable(false);
        orderDialog.setCanceledOnTouchOutside(false);

        Window w = orderDialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        // black, slightly transparent full-screen backdrop
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(OVERLAY_BG_COLOR);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.setPadding(dp(24), dp(24), dp(24), dp(24));

        orderTitle = new TextView(this);
        orderTitle.setText("LOOKING FOR A NEARBY HELPER");
        orderTitle.setTextColor(Color.WHITE);
        orderTitle.setTextSize(30);
        orderTitle.setTypeface(Typeface.DEFAULT_BOLD);
        orderTitle.setAllCaps(true);
        orderTitle.setGravity(Gravity.CENTER);
        col.addView(orderTitle);

        orderCenterIcon = new ImageView(this);
        LinearLayout.LayoutParams iconLL = new LinearLayout.LayoutParams(dp(220), dp(220));
        iconLL.topMargin = dp(28);
        iconLL.bottomMargin = dp(8);
        orderCenterIcon.setLayoutParams(iconLL);
        int pin = getResources().getIdentifier("location", "drawable", getPackageName());
        if (pin == 0) pin = android.R.drawable.ic_dialog_map;
        orderCenterIcon.setImageResource(pin);
        col.addView(orderCenterIcon);

        orderHelperName = new TextView(this);
        orderHelperName.setTextColor(Color.WHITE);
        orderHelperName.setTextSize(28);
        orderHelperName.setTypeface(Typeface.DEFAULT_BOLD);
        orderHelperName.setAllCaps(true);
        orderHelperName.setGravity(Gravity.CENTER);
        orderHelperName.setVisibility(View.GONE);
        orderHelperName.setPadding(0, dp(20), 0, dp(6));
        col.addView(orderHelperName);

        orderProfession = new TextView(this);
        orderProfession.setTextColor(Color.WHITE);
        orderProfession.setTextSize(22);
        orderProfession.setTypeface(Typeface.DEFAULT_BOLD);
        orderProfession.setAllCaps(true);
        orderProfession.setGravity(Gravity.CENTER);
        orderProfession.setVisibility(View.GONE);
        col.addView(orderProfession);

        orderStarsRow = new LinearLayout(this);
        orderStarsRow.setOrientation(LinearLayout.HORIZONTAL);
        orderStarsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams starsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        starsLp.topMargin = dp(16);
        orderStarsRow.setLayoutParams(starsLp);
        orderStarsRow.setVisibility(View.GONE);
        col.addView(orderStarsRow);

        orderCallBtn = new ImageView(this);
        LinearLayout.LayoutParams callLp = new LinearLayout.LayoutParams(dp(110), dp(110));
        callLp.topMargin = dp(24);
        orderCallBtn.setLayoutParams(callLp);
        orderCallBtn.setImageResource(android.R.drawable.sym_action_call);
        orderCallBtn.setColorFilter(Color.parseColor("#D4E157"));
        orderCallBtn.setVisibility(View.GONE);
        orderCallBtn.setOnClickListener(v -> {
            if (acceptedPhone != null && !acceptedPhone.trim().isEmpty()) {
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + acceptedPhone.trim()));
                startActivity(dial);
            } else {
                Toast.makeText(this, "Phone not available", Toast.LENGTH_SHORT).show();
            }
        });
        col.addView(orderCallBtn);

        orderCloseBtn = new ImageView(this);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(40), dp(40));
        closeLp.gravity = Gravity.TOP | Gravity.END;
        closeLp.topMargin = dp(20);
        closeLp.rightMargin = dp(20);
        orderCloseBtn.setLayoutParams(closeLp);
        orderCloseBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        orderCloseBtn.setColorFilter(Color.RED);
        orderCloseBtn.setVisibility(View.VISIBLE);
        orderCloseBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage("Cancel this request?")
                    .setPositiveButton("Yes", (d, wBtn) -> cancelActiveOrderIfSearching())
                    .setNegativeButton("No", null).show();
        });

        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.addView(col);
        rootContainer.addView(orderCloseBtn);

        root.addView(rootContainer);
        orderDialog.setContentView(root);
        orderDialog.show();
    }

    private void updateOrderOverlayAccepted(String helperName, String profession, @Nullable Double ratingAvg) {
        if (orderDialog == null || !orderDialog.isShowing()) return;
        lastOrderStatusShown = "accepted";

        orderTitle.setText("YOUR HELPER IS ON HIS WAY TO YOU");
        orderCenterIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(220), dp(220)));
        int check = getResources().getIdentifier("settings", "drawable", getPackageName());
        orderCenterIcon.setImageResource(check == 0 ? android.R.drawable.checkbox_on_background : check);

        orderHelperName.setText(helperName);
        orderHelperName.setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(profession)) {
            orderProfession.setText(profession);
            orderProfession.setVisibility(View.VISIBLE);
        }

        // draw stars based on numeric average (fallback to 5 if null)
        orderStarsRow.removeAllViews();
        int stars = ratingAvg == null ? 5 : Math.max(1, Math.min(5, (int) Math.round(ratingAvg)));
        for (int i = 0; i < stars; i++) {
            ImageView star = new ImageView(this);
            star.setImageResource(android.R.drawable.btn_star_big_on);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            if (i > 0) lp.leftMargin = dp(6);
            star.setLayoutParams(lp);
            orderStarsRow.addView(star);
        }
        orderStarsRow.setVisibility(View.VISIBLE);

        orderCallBtn.setVisibility(View.VISIBLE);
        orderCloseBtn.setVisibility(View.VISIBLE);
        orderCloseBtn.setOnClickListener(v -> dismissOrderOverlay());
    }

    private void updateOrderOverlayInProgress() {
        if (orderDialog == null || !orderDialog.isShowing()) showOrderOverlaySearching();
        lastOrderStatusShown = "in_progress";
        if (orderTitle != null) orderTitle.setText("YOUR HELP IS IN PROGRESS");
        if (orderCenterIcon != null) {
            int icon = getResources().getIdentifier("settings", "drawable", getPackageName());
            orderCenterIcon.setImageResource(icon == 0 ? android.R.drawable.ic_media_play : icon);
        }
        if (orderHelperName != null) orderHelperName.setVisibility(View.VISIBLE);
        if (orderCallBtn != null) orderCallBtn.setVisibility(View.VISIBLE);
        if (orderCloseBtn != null) {
            orderCloseBtn.setVisibility(View.VISIBLE);
            orderCloseBtn.setOnClickListener(v -> dismissOrderOverlay());
        }
    }

    private void dismissOrderOverlay() {
        if (orderDialog != null && orderDialog.isShowing()) orderDialog.dismiss();
        orderDialog = null;
        orderTitle = null;
        orderCenterIcon = null;
        orderHelperName = null;
        orderProfession = null;
        orderStarsRow = null;
        orderCallBtn = null;
        orderCloseBtn = null;

        if (orderListener != null) { orderListener.remove(); orderListener = null; }
        activeOrderId = null;
        acceptedPhone = null;

        if (feedbackDialog != null && feedbackDialog.isShowing()) {
            try { feedbackDialog.dismiss(); } catch (Throwable ignore) {}
        }
        feedbackDialog = null;
        lastOrderStatusShown = null;
    }

    private void cancelActiveOrderIfSearching() {
        if (activeOrderId == null) { dismissOrderOverlay(); return; }
        db.collection("orders").document(activeOrderId).get()
                .addOnSuccessListener(d -> {
                    if (d.exists() && "searching".equals(d.getString("status"))) {
                        db.collection("orders").document(activeOrderId)
                                .update("status","cancelled","cancelledAt", FieldValue.serverTimestamp())
                                .addOnSuccessListener(v -> {
                                    Toast.makeText(this,"Order cancelled",Toast.LENGTH_SHORT).show();
                                    dismissOrderOverlay();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this,"Cancel failed: "+e.getMessage(),Toast.LENGTH_SHORT).show());
                    } else {
                        dismissOrderOverlay();
                    }
                });
    }

    private int dp(int d) {
        float den = getResources().getDisplayMetrics().density;
        return Math.round(d * den);
    }

    // ===== HELPER: orders list + accept/deny =====
    private void setupHelperOrdersList() {
        if (helperRecycler == null) return;

        if (helperOrdersSub != null) { helperOrdersSub.remove(); helperOrdersSub = null; }

        if (helperAdapter == null) helperAdapter = new HelperOrdersAdapter(new ArrayList<>());
        helperRecycler.setAdapter(helperAdapter);

        Query q = db.collection("orders").whereEqualTo("status", "searching");
        final String myUid = auth.getUid();

        helperOrdersSub = q.addSnapshotListener((snaps, e) -> {
            if (e != null || snaps == null) return;

            ArrayList<DocumentSnapshot> visible = new ArrayList<>();
            for (DocumentSnapshot d : snaps.getDocuments()) {
                List<String> denied = (List<String>) d.get("deniedBy");
                if (myUid != null && denied != null && denied.contains(myUid)) continue;
                visible.add(d);
            }

            java.util.Collections.sort(visible, (a, b) -> {
                Timestamp ta = a.getTimestamp("createdAt");
                Timestamp tb = b.getTimestamp("createdAt");
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });

            helperAdapter.submit(visible);
        });
    }

    // ==== Helper accept dialog
    private void showHelperAcceptDialog(DocumentSnapshot d) {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        helperAcceptDialog = dlg;
        dlg.setOnDismissListener(dialog -> helperAcceptDialog = null);

        final String orderId = d.getId();
        final String addressText = TextUtils.isEmpty(d.getString("address")) ? "" : d.getString("address");
        final Double oLat = d.getDouble("lat");
        final Double oLng = d.getDouble("lng");

        String distanceUi = null;
        if (oLat != null && oLng != null && currentLat != null && currentLng != null) {
            float[] res = new float[1];
            Location.distanceBetween(currentLat, currentLng, oLat, oLng, res);
            distanceUi = (formatDistance(res[0]) + " AWAY FROM YOU").toUpperCase(Locale.ROOT);
        }

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(OVERLAY_BG_COLOR); // black translucent

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#CC000000"));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = dp(24);
        cardLp.rightMargin = dp(24);
        cardLp.topMargin = dp(40);
        cardLp.bottomMargin = dp(40);
        card.setLayoutParams(cardLp);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView line1 = new TextView(this);
        String userName = d.getString("userName");
        if (TextUtils.isEmpty(userName)) userName = "CUSTOMER";
        line1.setText(userName.toUpperCase(Locale.ROOT));
        line1.setTextColor(Color.WHITE);
        line1.setTypeface(Typeface.DEFAULT_BOLD);
        line1.setTextSize(24);
        line1.setPadding(0, dp(8),0,dp(4));
        card.addView(line1);

        TextView line2 = new TextView(this);
        line2.setTextColor(Color.WHITE);
        line2.setTypeface(Typeface.DEFAULT_BOLD);
        line2.setTextSize(18);
        line2.setAlpha(0.95f);
        if (!TextUtils.isEmpty(distanceUi)) line2.setText(stylizeDistance(distanceUi));
        else line2.setText(addressText.toUpperCase(Locale.ROOT));
        card.addView(line2);

        View div = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        divLp.topMargin = divLp.bottomMargin = dp(12);
        div.setLayoutParams(divLp);
        div.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        card.addView(div);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(8), 0, dp(6));

        LinearLayout locCol = new LinearLayout(this);
        locCol.setOrientation(LinearLayout.VERTICAL);
        locCol.setGravity(Gravity.CENTER);
        ImageView locIcon = new ImageView(this);
        int pin = getResources().getIdentifier("location", "drawable", getPackageName());
        locIcon.setImageResource(pin == 0 ? android.R.drawable.ic_dialog_map : pin);
        locIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(96), dp(96)));
        locCol.addView(locIcon);
        TextView locLbl = new TextView(this);
        locLbl.setText("LOCATION");
        locLbl.setTextColor(Color.WHITE);
        locLbl.setTypeface(Typeface.DEFAULT_BOLD);
        locLbl.setAllCaps(true);
        locLbl.setTextSize(16);
        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lblLp.topMargin = dp(8);
        locLbl.setLayoutParams(lblLp);
        locCol.addView(locLbl);

        LinearLayout doneCol = new LinearLayout(this);
        doneCol.setOrientation(LinearLayout.VERTICAL);
        doneCol.setGravity(Gravity.CENTER);
        ImageView doneIcon = new ImageView(this);
        int tick = getResources().getIdentifier("tick2", "drawable", getPackageName());
        if (tick == 0) tick = getResources().getIdentifier("tick", "drawable", getPackageName());
        doneIcon.setImageResource(tick == 0 ? android.R.drawable.checkbox_on_background : tick);
        doneIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(96), dp(96)));
        doneCol.addView(doneIcon);
        TextView doneLbl = new TextView(this);
        doneLbl.setText("COMPLETED");
        doneLbl.setTextColor(Color.WHITE);
        doneLbl.setTypeface(Typeface.DEFAULT_BOLD);
        doneLbl.setAllCaps(true);
        doneLbl.setTextSize(16);
        LinearLayout.LayoutParams doneLblLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneLblLp.topMargin = dp(8);
        doneLbl.setLayoutParams(doneLblLp);
        doneCol.addView(doneLbl);

        row.addView(locCol);
        row.addView(new View(this), new LinearLayout.LayoutParams(dp(40), 1));
        row.addView(doneCol);
        card.addView(row);

        overlay.addView(card);
        dlg.setContentView(overlay);
        dlg.show();

        locCol.setOnClickListener(v -> db.collection("orders").document(orderId)
                .update("status","in_progress","startedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(x -> {
                    startHelperLiveTracking(orderId);
                    openGoogleMaps(addressText, oLat, oLng);
                })
                .addOnFailureListener(err ->
                        Toast.makeText(this, "Failed: " + err.getMessage(), Toast.LENGTH_SHORT).show())) ;

        // Finish flow respecting rules
        doneCol.setOnClickListener(v -> completeOrderSmart(orderId, dlg));
    }

    private CharSequence stylizeDistance(String textUpper) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(textUpper);
        int firstSpace = textUpper.indexOf(' ');
        if (firstSpace > 0) {
            ssb.setSpan(new ForegroundColorSpan(Color.parseColor("#D8E106")),
                    0, firstSpace, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    private void openGoogleMaps(@Nullable String address, @Nullable Double lat, @Nullable Double lng) {
        try {
            Intent mapIntent;
            if (lat != null && lng != null) {
                Uri nav = Uri.parse("google.navigation:q=" + lat + "," + lng);
                mapIntent = new Intent(Intent.ACTION_VIEW, nav);
            } else if (!TextUtils.isEmpty(address)) {
                Uri nav = Uri.parse("google.navigation:q=" + Uri.encode(address));
                mapIntent = new Intent(Intent.ACTION_VIEW, nav);
            } else {
                Toast.makeText(this, "No location data", Toast.LENGTH_SHORT).show();
                return;
            }
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        } catch (Exception e) {
            Intent fallback;
            if (lat != null && lng != null) {
                fallback = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng));
            } else {
                fallback = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=" + Uri.encode(address == null ? "" : address)));
            }
            startActivity(fallback);
        }
    }

    private void showLocationDialog(@Nullable String address, @Nullable Double lat, @Nullable Double lng) {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        navDialog = dlg;
        dlg.setOnDismissListener(dialog -> navDialog = null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));
        root.setBackgroundColor(OVERLAY_BG_COLOR); // black translucent
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(address == null ? "LOCATION" : address.toUpperCase(Locale.ROOT));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setAllCaps(true);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView openMaps = buildPillButton("START NAVIGATION");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        openMaps.setLayoutParams(lp);
        root.addView(openMaps);

        openMaps.setOnClickListener(v -> openGoogleMaps(address, lat, lng));

        dlg.setContentView(root);
        dlg.show();
    }

    private TextView buildPillButton(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setAllCaps(true);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(16);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        tv.setBackground(new ColorDrawable(Color.parseColor("#E6EE9C")));
        return tv;
    }

    // ===== models & adapters =====
    private static class Suggestion {
        final String title;
        final double lat, lon;
        Suggestion(String t, double la, double lo) { title = t; lat = la; lon = lo; }
    }

    private class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionsAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView title;
            VH(View v) { super(v); title = v.findViewById(R.id.title); }
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.row_prediction, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            Suggestion s = suggestions.get(pos);
            h.title.setText(s.title.toUpperCase(Locale.ROOT));
            h.itemView.setOnClickListener(v -> {
                setChosenLocation(s.title, s.lat, s.lon, true);
                toggleSearchPanel(false);
            });
        }
        @Override public int getItemCount() { return suggestions.size(); }
    }

    private static class SavedListAdapter extends android.widget.BaseAdapter {
        interface OnChange {
            void onSelect(Suggestion s);
            void onDelete(int position);
        }

        private final ArrayList<Suggestion> data;
        private final OnChange cb;

        SavedListAdapter(ArrayList<Suggestion> data, OnChange cb) { this.data = data; this.cb = cb; }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(parent.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(parent,12), dp(parent,12), dp(parent,12), dp(parent,12));
                row.setGravity(Gravity.CENTER_VERTICAL);

                TextView name = new TextView(parent.getContext());
                LinearLayout.LayoutParams lpName =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                name.setLayoutParams(lpName);
                name.setTextSize(16);
                name.setTextColor(Color.WHITE);

                ImageView del = new ImageView(parent.getContext());
                LinearLayout.LayoutParams lpDel =
                        new LinearLayout.LayoutParams(dp(parent,24), dp(parent,24));
                del.setLayoutParams(lpDel);
                del.setImageResource(android.R.drawable.ic_menu_delete);
                del.setColorFilter(Color.parseColor("#C62028"));

                row.addView(name);
                row.addView(del);

                h = new ViewHolder();
                h.title = name;
                h.delete = del;
                row.setTag(h);
                convertView = row;
            } else {
                h = (ViewHolder) convertView.getTag();
            }

            Suggestion s = data.get(position);
            h.title.setText(s.title);
            convertView.setOnClickListener(v -> cb.onSelect(s));
            h.delete.setOnClickListener(v -> cb.onDelete(position));
            return convertView;
        }

        private static int dp(ViewGroup parent, int d) {
            float den = parent.getResources().getDisplayMetrics().density;
            return Math.round(d * den);
        }

        static class ViewHolder {
            TextView title;
            ImageView delete;
        }
    }

    // ===== Helper Recycler Adapter =====
    private class HelperOrdersAdapter extends RecyclerView.Adapter<HelperOrdersAdapter.VH> {
        ArrayList<DocumentSnapshot> data;
        HelperOrdersAdapter(ArrayList<DocumentSnapshot> d){ data = d; }
        void submit(ArrayList<DocumentSnapshot> d){ data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvNameNeeds, tvDistance;
            ImageButton btnDeny, btnAccept;
            VH(View v){
                super(v);
                tvNameNeeds = v.findViewById(R.id.tvNameNeeds);
                tvDistance = v.findViewById(R.id.tvDistance);
                btnDeny = v.findViewById(R.id.btnDeny);
                btnAccept = v.findViewById(R.id.btnAccept);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t){
            View v = getLayoutInflater().inflate(R.layout.row_helper_order, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos){
            DocumentSnapshot d = data.get(pos);

            String userName = d.getString("userName");
            if (userName == null || userName.trim().isEmpty()) userName = "CUSTOMER";
            h.tvNameNeeds.setText(userName.toUpperCase(Locale.ROOT));

            Double lat = d.getDouble("lat"), lng = d.getDouble("lng");
            String distStr = "AWAY FROM YOU";
            if (lat != null && lng != null && currentLat != null && currentLng != null){
                float[] res = new float[1];
                Location.distanceBetween(currentLat, currentLng, lat, lng, res);
                distStr = (formatDistance(res[0]) + " AWAY FROM YOU").toUpperCase(Locale.ROOT);
                h.tvDistance.setText(stylizeDistance(distStr));
            } else {
                h.tvDistance.setText(distStr);
            }

            h.btnDeny.setOnClickListener(v -> {
                String uid = auth.getUid();
                if (uid == null) return;
                db.collection("orders").document(d.getId())
                        .update("deniedBy", FieldValue.arrayUnion(uid))
                        .addOnSuccessListener(x -> Toast.makeText(HomeActivity.this, "Denied", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(err ->
                                Toast.makeText(HomeActivity.this, "Failed: " + err.getMessage(), Toast.LENGTH_SHORT).show());
            });

            h.btnAccept.setOnClickListener(v -> {
                v.setEnabled(false);
                FirebaseUser me = auth.getCurrentUser();
                if (me == null) { v.setEnabled(true); return; }

                db.collection("users").document(me.getUid()).get()
                        .addOnSuccessListener(p -> acceptOrderTransaction(d.getId(), p, me))
                        .addOnFailureListener(err -> {
                            v.setEnabled(true);
                            Toast.makeText(HomeActivity.this, "Profile read failed: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            });
        }

        @Override public int getItemCount(){ return data.size(); }
    }

    private String formatDistance(float meters) {
        if (meters < 950) return Math.round(meters) + "M";
        return String.format(Locale.US, "%.1fKM", meters / 1000f);
    }

    // Bridge for map clicks
    private class MapBridge {
        @android.webkit.JavascriptInterface
        public void onPick(final double lat, final double lng) {
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return;
            runOnUiThread(() -> reverseGeocode(lat, lng));
        }
    }

    private String deriveUserName(@Nullable DocumentSnapshot profile, @Nullable FirebaseUser u) {
        if (profile != null) {
            for (String key : new String[]{"name", "userName", "username", "fullName", "displayName"}) {
                String v = profile.getString(key);
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
            String first = profile.getString("firstName");
            String last  = profile.getString("lastName");
            if (!TextUtils.isEmpty(first) || !TextUtils.isEmpty(last)) {
                return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            }
        }
        if (u != null && !TextUtils.isEmpty(u.getDisplayName())) return u.getDisplayName().trim();
        if (u != null) {
            if (!TextUtils.isEmpty(u.getEmail())) {
                String email = u.getEmail();
                int at = email.indexOf('@');
                String local = email.substring(0, at > 0 ? at : email.length());
                if (!TextUtils.isEmpty(local)) return local;
            }
            if (!TextUtils.isEmpty(u.getPhoneNumber())) return u.getPhoneNumber();
        }
        return "User";
    }

    // Keep newest active order in sync
    private void attachUserActiveOrderListenerIfUser() {
        if (!"user".equalsIgnoreCase(role)) {
            if (userActiveOrderReg != null) { userActiveOrderReg.remove(); userActiveOrderReg = null; }
            return;
        }
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;

        if (userActiveOrderReg != null) userActiveOrderReg.remove();
        userActiveOrderReg = db.collection("orders")
                .whereEqualTo("userId", u.getUid())
                .whereIn("status", Arrays.asList("searching","accepted","in_progress","completed"))
                .addSnapshotListener((snap, err) -> {
                    if (err != null) return;
                    if (snap == null || snap.isEmpty()) { dismissOrderOverlay(); activeOrderId = null; return; }

                    DocumentSnapshot newest = null; Timestamp best = null;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Timestamp ts = d.getTimestamp("createdAt");
                        if (newest == null || (ts != null && (best == null || ts.compareTo(best) > 0))) {
                            newest = d; best = ts;
                        }
                    }
                    if (newest == null) return;

                    String st = newest.getString("status");

                    if (newest.getId().equals(overlaySuppressedOrderId) &&
                            st != null && st.equals(overlaySuppressedStatus)) {
                        return;
                    } else {
                        overlaySuppressedOrderId = null;
                        overlaySuppressedStatus = null;
                    }

                    activeOrderId = null;

                    if ("searching".equals(st)) {
                        activeOrderId = newest.getId();
                        showOrderOverlaySearching();
                    } else if ("accepted".equals(st)) {
                        activeOrderId = newest.getId();
                        String helperName = newest.getString("helperName");
                        String profession = newest.getString("helperProfession");
                        Double ratingAvg = getHelperRatingFromOrder(newest); // numeric
                        acceptedPhone = newest.getString("helperPhone");
                        updateOrderOverlayAccepted(
                                helperName == null ? "YOUR HELPER" : helperName,
                                profession == null ? "" : profession,
                                ratingAvg
                        );
                    } else if ("in_progress".equals(st)) {
                        activeOrderId = newest.getId();
                        updateOrderOverlayInProgress();
                    } else if ("completed".equals(st)) {
                        if (newest.get("userRating") == null) showFeedbackDialog(newest);
                        else dismissOrderOverlay();
                    }

                    Object locObj = newest.get("helperLoc");
                    if (locObj instanceof java.util.Map && currentLat != null && currentLng != null) {
                        Double hLat = toDouble(((java.util.Map<?, ?>) locObj).get("lat"));
                        Double hLng = toDouble(((java.util.Map<?, ?>) locObj).get("lng"));
                        if (hLat != null && hLng != null) {
                            evaluateJS("updHelper(" + hLat + "," + hLng + "," + currentLat + "," + currentLng + ")");
                        }
                    }
                });
    }

    private Double toDouble(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof Long) return ((Long) o).doubleValue();
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        return null;
    }

    // Prefer numeric rating copied to the order
    private Double getHelperRatingFromOrder(DocumentSnapshot snap) {
        Double r = snap.getDouble("helperRatingAvg");
        if (r == null) r = snap.getDouble("helperRating"); // backward-compat if old data
        return r;
    }

    // Prevent double-claim; fill helper info + numeric rating snapshot
    private void acceptOrderTransaction(String orderId, @Nullable DocumentSnapshot helperProfile, @Nullable FirebaseUser me) {
        if (me == null) return;
        String helperName = helperProfile == null ? null : helperProfile.getString("name");
        String profession  = helperProfile == null ? null : helperProfile.getString("profession");
        String phone       = helperProfile == null ? null : helperProfile.getString("phone");

        // numeric ratings from user doc
        Double ratingAvg   = helperProfile == null ? null : helperProfile.getDouble("ratingAvg");
        Long   ratingCount = (helperProfile == null) ? null : helperProfile.getLong("ratingCount");

        final String fHelperName = helperName;
        final String fProfession = profession;
        final String fPhone = phone;
        final Double fRatingAvg = ratingAvg;
        final Long   fRatingCount = ratingCount;
        final String fUid = me.getUid();

        db.runTransaction((Transaction.Function<Void>) tr -> {
                    DocumentSnapshot d = tr.get(db.collection("orders").document(orderId));
                    String st = d.getString("status");
                    if (!"searching".equals(st)) {
                        throw new FirebaseFirestoreException("Already claimed", FirebaseFirestoreException.Code.ABORTED);
                    }
                    HashMap<String,Object> upd = new HashMap<>();
                    upd.put("status","accepted");
                    upd.put("helperId", fUid);
                    if (!TextUtils.isEmpty(fHelperName)) upd.put("helperName", fHelperName);
                    if (!TextUtils.isEmpty(fProfession))  upd.put("helperProfession", fProfession);
                    if (!TextUtils.isEmpty(fPhone))       upd.put("helperPhone", fPhone);
                    // Copy numeric rating fields for stable UI
                    if (fRatingAvg != null)   upd.put("helperRatingAvg", fRatingAvg);
                    if (fRatingCount != null) upd.put("helperRatingCount", fRatingCount);
                    upd.put("acceptedAt", FieldValue.serverTimestamp());
                    tr.update(db.collection("orders").document(orderId), upd);
                    return null;
                }).addOnSuccessListener(v -> db.collection("orders").document(orderId).get()
                        .addOnSuccessListener(this::showHelperAcceptDialog))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Accept failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ===== helper live tracking (sends helperLoc to order) =====
    @SuppressLint("MissingPermission")
    private void startHelperLiveTracking(String orderId) {
        if (helperTracking) return;
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locationPermsLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        helperTracking = true;

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(3f)
                .build();

        helperLocCb = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                Location l = result.getLastLocation();
                if (l == null) return;
                HashMap<String,Object> loc = new HashMap<>();
                loc.put("lat", l.getLatitude());
                loc.put("lng", l.getLongitude());
                loc.put("ts", FieldValue.serverTimestamp());
                db.collection("orders").document(orderId).update("helperLoc", loc);
            }
        };
        fused.requestLocationUpdates(req, helperLocCb, Looper.getMainLooper());
    }

    private void stopHelperLiveTracking() {
        if (!helperTracking) return;
        try { if (helperLocCb != null) fused.removeLocationUpdates(helperLocCb); } catch (Throwable ignore) {}
        helperLocCb = null;
        helperTracking = false;
    }

    // ===== helper list live location (for distance display) =====
    @SuppressLint("MissingPermission")
    private void startHelperListLocUpdates() {
        if (helperListLocUpdates) return;

        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) return;

        helperListLocUpdates = true;

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 7000L)
                .setMinUpdateIntervalMillis(4000L)
                .setMinUpdateDistanceMeters(5f)
                .build();

        helperListLocCb = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                Location l = result.getLastLocation();
                if (l == null) return;
                currentLat = l.getLatitude();
                currentLng = l.getLongitude();
                if (helperAdapter != null) helperAdapter.notifyDataSetChanged();
            }
        };
        fused.requestLocationUpdates(req, helperListLocCb, Looper.getMainLooper());
    }

    private void stopHelperListLocUpdates() {
        if (!helperListLocUpdates) return;
        try { if (helperListLocCb != null) fused.removeLocationUpdates(helperListLocCb); } catch (Throwable ignore) {}
        helperListLocCb = null;
        helperListLocUpdates = false;
    }

    // ===== feedback (user after completion) =====
    private void showFeedbackDialog(DocumentSnapshot orderSnap) {
        if (!"user".equalsIgnoreCase(role)) return;

        final String orderId = orderSnap.getId();
        final String helperName = orderSnap.getString("helperName");
        final String profession = orderSnap.getString("helperProfession");
        final String helperId = orderSnap.getString("helperId");

        // show only once per order
        if (orderId.equals(ratingShownForOrderId)) return;
        if (feedbackDialog != null && feedbackDialog.isShowing()) return;
        ratingShownForOrderId = orderId;

        feedbackDialog = new Dialog(this);
        feedbackDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        feedbackDialog.setCancelable(false);
        if (feedbackDialog.getWindow() != null)
            feedbackDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));
        root.setBackgroundColor(OVERLAY_BG_COLOR); // black translucent
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("TELL US YOUR FEEDBACK");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setAllCaps(true);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView name = new TextView(this);
        name.setText((helperName == null ? "YOUR HELPER" : helperName).toUpperCase(Locale.ROOT));
        name.setTextColor(Color.WHITE);
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(0, dp(16), 0, dp(6));
        root.addView(name);

        if (!TextUtils.isEmpty(profession)) {
            TextView prof = new TextView(this);
            prof.setText(profession.toUpperCase(Locale.ROOT));
            prof.setTextColor(Color.WHITE);
            prof.setTypeface(Typeface.DEFAULT_BOLD);
            prof.setTextSize(18);
            root.addView(prof);
        }

        LinearLayout starsRow = new LinearLayout(this);
        starsRow.setOrientation(LinearLayout.HORIZONTAL);
        starsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams starsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        starsLp.topMargin = dp(18);
        starsRow.setLayoutParams(starsLp);
        root.addView(starsRow);

        final int[] chosen = {5};
        final ArrayList<ImageView> starViews = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int value = i;
            ImageView star = new ImageView(this);
            star.setImageResource(android.R.drawable.btn_star_big_on);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            if (i > 1) lp.leftMargin = dp(6);
            star.setLayoutParams(lp);
            star.setOnClickListener(v -> {
                chosen[0] = value;
                for (int j = 0; j < 5; j++) {
                    starViews.get(j).setImageResource(j < value ? android.R.drawable.btn_star_big_on
                            : android.R.drawable.btn_star_big_off);
                }
            });
            starViews.add(star);
            starsRow.addView(star);
        }

        TextView btnDone = buildPillButton("DONE");
        LinearLayout.LayoutParams lpDone = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpDone.topMargin = dp(20);
        btnDone.setLayoutParams(lpDone);
        root.addView(btnDone);

        btnDone.setOnClickListener(v -> {
            if (feedbackDialog != null && feedbackDialog.isShowing()) feedbackDialog.dismiss();
            submitHelperRating(orderId, helperId, chosen[0]);
            dismissOrderOverlay();
        });

        feedbackDialog.setContentView(root);
        feedbackDialog.show();
    }

    /**
     * STEP 1: write the order rating (required by rules)
     * STEP 2: bump helper aggregates in a separate transaction (best-effort)
     */
    private void submitHelperRating(String orderId, @Nullable String helperId, int stars) {
        if (helperId == null) {
            Toast.makeText(this, "Rating failed: missing helper id", Toast.LENGTH_LONG).show();
            return;
        }

        final int fStars = Math.max(1, Math.min(5, stars));
        final DocumentReference orderRef  = db.collection("orders").document(orderId);
        final DocumentReference helperRef = db.collection("users").document(helperId);

        // Read to verify then update ONLY the fields allowed by rules
        orderRef.get().addOnSuccessListener(ord -> {
            if (ord == null || !ord.exists()) {
                Toast.makeText(this, "Rating failed: order not found", Toast.LENGTH_LONG).show();
                return;
            }
            if (!"completed".equals(ord.getString("status"))) {
                Toast.makeText(this, "You can rate after completion.", Toast.LENGTH_LONG).show();
                return;
            }
            if (ord.contains("userRating")) {
                Toast.makeText(this, "Already rated. Thank you!", Toast.LENGTH_SHORT).show();
                ratingShownForOrderId = orderId;
                return;
            }

            // STEP 1: Update order (only userRating & ratedAt)
            orderRef.update("userRating", fStars, "ratedAt", FieldValue.serverTimestamp())
                    .addOnSuccessListener(v -> {
                        ratingShownForOrderId = orderId;
                        Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show();

                        // STEP 2 (best-effort): bump helper aggregates
                        db.runTransaction((Transaction.Function<Void>) tr -> {
                            DocumentSnapshot prof = tr.get(helperRef);
                            long   oldCount = prof.contains("ratingCount") ? prof.getLong("ratingCount") : 0L;
                            double oldSum   = prof.contains("ratingSum")   ? prof.getDouble("ratingSum")   : 0.0;
                            long   newCount = oldCount + 1L;
                            double newSum   = oldSum + (double) fStars;
                            double newAvg   = newSum / (double) newCount;

                            HashMap<String, Object> bump = new HashMap<>();
                            bump.put("ratingCount", newCount);
                            bump.put("ratingSum",   newSum);
                            bump.put("ratingAvg",   newAvg);
                            tr.update(helperRef, bump);
                            return null;
                        }).addOnFailureListener(e ->
                                android.util.Log.w(TAG, "Aggregate bump failed (order is rated OK): " + e.getMessage()));
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Rating failed: " + e.getMessage(), Toast.LENGTH_LONG).show());

        }).addOnFailureListener(e ->
                Toast.makeText(this, "Rating failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // ===== Helper completion that respects security rules =====
    private void completeOrderSmart(String orderId, @Nullable Dialog dlg) {
        stopHelperLiveTracking();

        final DocumentReference ref = db.collection("orders").document(orderId);

        // Try normal path: in_progress -> completed
        ref.update("status", "completed", "completedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(x -> {
                    Toast.makeText(this, "Marked as completed", Toast.LENGTH_SHORT).show();
                    if (dlg != null) dlg.dismiss();
                })
                .addOnFailureListener(err -> {
                    // If denied, check if we're still at 'accepted' and do a two-step
                    ref.get().addOnSuccessListener(snap -> {
                        String st = snap.getString("status");
                        String helperId = snap.getString("helperId");
                        FirebaseUser me = auth.getCurrentUser();

                        if ("accepted".equals(st) && me != null && me.getUid().equals(helperId)) {
                            // Step 1: accepted -> in_progress
                            ref.update("status", "in_progress", "startedAt", FieldValue.serverTimestamp())
                                    .addOnSuccessListener(v1 -> {
                                        // Step 2: in_progress -> completed
                                        ref.update("status", "completed", "completedAt", FieldValue.serverTimestamp())
                                                .addOnSuccessListener(v2 -> {
                                                    Toast.makeText(this, "Marked as completed", Toast.LENGTH_SHORT).show();
                                                    if (dlg != null) dlg.dismiss();
                                                })
                                                .addOnFailureListener(e2 ->
                                                        Toast.makeText(this, "Failed: " + e2.getMessage(), Toast.LENGTH_SHORT).show());
                                    })
                                    .addOnFailureListener(e1 ->
                                            Toast.makeText(this, "Failed: " + e1.getMessage(), Toast.LENGTH_SHORT).show());
                        } else {
                            Toast.makeText(this, "Failed: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(e ->
                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                });
    }
}
