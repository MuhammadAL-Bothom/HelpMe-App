package com.example.helpme;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Helper landing screen:
 * - Loads orders from Firestore with status="searching"
 * - Renders rows that match the provided mock
 * - Accept = show dialog (mock #2), Deny = mark in order doc (optional) and hide row
 */
public class HelperActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fused;

    private LinearLayout listContainer; // holds the stacked rows
    private final List<View> rowViews = new ArrayList<>();

    private Location helperLoc;

    private final ActivityResultLauncher<String[]> locPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> fetchMyLocation());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        fused= LocationServices.getFusedLocationProviderClient(this);

        setContentView(buildRoot());      // programmatic UI to match the mock
        ensureLocationThenLoad();
    }

    private View buildRoot() {
        // Page root
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F3F3F3"));

        // Header (white)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.WHITE);
        header.setPadding(dp(12), dp(18), dp(12), dp(18));
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView burger = new ImageView(this);
        burger.setImageResource(android.R.drawable.ic_menu_sort_by_size); // simple burger
        burger.setColorFilter(Color.parseColor("#D32F2F"));
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(dp(36), dp(36));
        lpB.rightMargin = dp(12);
        burger.setLayoutParams(lpB);
        header.addView(burger);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView app = t("HELP ME APP", 22, "#D32F2F", true, Gravity.CENTER_HORIZONTAL);
        TextView role = t("HELPER", 20, "#000000", true, Gravity.CENTER_HORIZONTAL);
        titles.addView(app);
        titles.addView(role);
        header.addView(titles);

        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scroll list area (grey blocks separated by white lines)
        ScrollView sv = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listContainer, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private TextView t(String text, int sp, String color, boolean bold, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setAllCaps(true);
        tv.setTypeface(Typeface.DEFAULT_BOLD, bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setGravity(gravity);
        return tv;
    }

    private int dp(int d) {
        float den = getResources().getDisplayMetrics().density;
        return Math.round(d * den);
    }

    private void ensureLocationThenLoad() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locPerms.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        } else {
            fetchMyLocation();
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchMyLocation() {
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    helperLoc = loc;
                    loadOrders();
                })
                .addOnFailureListener(e -> loadOrders());
    }

    private void loadOrders() {
        listContainer.removeAllViews();
        rowViews.clear();

        db.collection("orders")
                .whereEqualTo("status", "searching")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        TextView empty = t("NO REQUESTS RIGHT NOW", 16, "#666666", true, Gravity.CENTER_HORIZONTAL);
                        empty.setPadding(0, dp(24), 0, dp(24));
                        listContainer.addView(empty);
                        return;
                    }
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        listContainer.addView(buildRow(
                                doc.getId(),
                                doc.getString("userName"),
                                doc.getString("address"),
                                doc.getDouble("lat"),
                                doc.getDouble("lng")
                        ));
                        // divider
                        View divider = new View(this);
                        divider.setBackgroundColor(Color.WHITE);
                        listContainer.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private View buildRow(String orderId, @Nullable String userName, @Nullable String address, @Nullable Double lat, @Nullable Double lng) {
        String name = TextUtils.isEmpty(userName) ? "UNKNOWN" : userName;

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundColor(Color.parseColor("#7A7A7A")); // grey
        block.setPadding(dp(16), dp(16), dp(16), dp(16));

        // line1: name + “needs help”
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView tvName = t(name, 18, "#FFFFFF", true, Gravity.CENTER);
        TextView tvNeed = t("  NEEDS HELP", 18, "#FFFFFF", true, Gravity.CENTER);
        top.addView(tvName);
        top.addView(tvNeed);
        block.addView(top);

        // line2: distance
        String distStr = calcDistanceLatLng(lat, lng);
        TextView tvDist = t(distStr + " AWAY FROM YOU", 18, "#DDE500", true, Gravity.CENTER);
        tvDist.setPadding(0, dp(12), 0, dp(8));
        block.addView(tvDist);

        // line3: X and ✓ buttons
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);

        ImageButton deny = new ImageButton(this);
        deny.setBackgroundColor(Color.TRANSPARENT);
        deny.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        deny.setColorFilter(Color.parseColor("#D32F2F"));
        LinearLayout.LayoutParams lpX = new LinearLayout.LayoutParams(dp(56), dp(56));
        lpX.rightMargin = dp(48);
        actions.addView(deny, lpX);

        ImageButton accept = new ImageButton(this);
        accept.setBackgroundColor(Color.TRANSPARENT);
        accept.setImageResource(android.R.drawable.checkbox_on_background);
        accept.setColorFilter(Color.parseColor("#D4E157"));
        actions.addView(accept, new LinearLayout.LayoutParams(dp(56), dp(56)));

        block.addView(actions);

        // Wire clicks
        deny.setOnClickListener(v -> denyOrder(orderId, block));
        accept.setOnClickListener(v -> acceptOrder(orderId, name, address, lat, lng));

        rowViews.add(block);
        return block;
    }

    private String calcDistanceLatLng(@Nullable Double lat, @Nullable Double lng) {
        if (lat == null || lng == null || helperLoc == null) return "—";
        float[] out = new float[1];
        Location.distanceBetween(helperLoc.getLatitude(), helperLoc.getLongitude(), lat, lng, out);
        float m = out[0];
        if (m < 1000f) {
            return String.format(Locale.US, "%.0fM", m);
        }
        return String.format(Locale.US, "%.1fKM", m / 1000f);
    }

    private void denyOrder(String orderId, View row) {
        String uid = auth.getCurrentUser() == null ? "anon" : auth.getCurrentUser().getUid();
        db.collection("orders").document(orderId)
                .update("deniedBy", FieldValue.arrayUnion(uid))
                .addOnSuccessListener(unused -> {
                    // simply hide the row
                    row.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void acceptOrder(String orderId, String customerName, @Nullable String address, @Nullable Double lat, @Nullable Double lng) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Sign in first", Toast.LENGTH_SHORT).show();
            return;
        }
        String helperId = auth.getCurrentUser().getUid();
        String helperName = auth.getCurrentUser().getDisplayName();
        if (TextUtils.isEmpty(helperName)) helperName = "HELPER";

        // Set as accepted by this helper
        DocumentReference ref = db.collection("orders").document(orderId);
        ref.update(
                        "status", "accepted",
                        "helperId", helperId,
                        "helperName", helperName,
                        "acceptedAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> showAcceptedDialog(orderId, customerName, address, lat, lng))
                .addOnFailureListener(e -> Toast.makeText(this, "Accept failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /** Dialog like image #2: shows name, car (if present), color (if present), Location & Completed buttons */
    private void showAcceptedDialog(String orderId, String customerName, @Nullable String address, @Nullable Double lat, @Nullable Double lng) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.setCancelable(false);

        // dark overlay container
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.parseColor("#CC000000"));
        box.setPadding(dp(22), dp(22), dp(22), dp(22));

        // Title lines
        TextView t1 = t(customerName, 26, "#FFFFFF", true, Gravity.CENTER_HORIZONTAL);
        t1.setPadding(0, dp(8), 0, dp(8));
        box.addView(t1);

        // Optional car + color from order fields (read once)
        db.collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            String car = doc.getString("carModel");
            String color = doc.getString("carColor");
            if (!TextUtils.isEmpty(car)) {
                TextView tvCar = t(car, 22, "#FFFFFF", true, Gravity.CENTER_HORIZONTAL);
                tvCar.setPadding(0, dp(6), 0, dp(6));
                box.addView(tvCar, 1); // under name
            }
            if (!TextUtils.isEmpty(color)) {
                TextView tvColor = t(color, 22, "#FFFFFF", true, Gravity.CENTER_HORIZONTAL);
                tvColor.setPadding(0, dp(6), 0, dp(16));
                box.addView(tvColor, 2);
            }
        });

        // Icon row (Location + Completed)
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(24), 0, dp(8));

        // Location
        LinearLayout locCol = new LinearLayout(this);
        locCol.setOrientation(LinearLayout.VERTICAL);
        locCol.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView locIc = new ImageView(this);
        locIc.setImageResource(android.R.drawable.ic_menu_mylocation);
        locIc.setColorFilter(Color.parseColor("#D4E157"));
        locCol.addView(locIc, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView locLbl = t("LOCATION", 16, "#FFFFFF", true, Gravity.CENTER);
        locLbl.setPadding(0, dp(6), 0, 0);
        locCol.addView(locLbl);

        // Completed
        LinearLayout doneCol = new LinearLayout(this);
        doneCol.setOrientation(LinearLayout.VERTICAL);
        doneCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneLp.leftMargin = dp(36);

        ImageView doneIc = new ImageView(this);
        doneIc.setImageResource(android.R.drawable.checkbox_on_background);
        doneIc.setColorFilter(Color.parseColor("#D4E157"));
        doneCol.addView(doneIc, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView doneLbl = t("COMPLETED", 16, "#FFFFFF", true, Gravity.CENTER);
        doneLbl.setPadding(0, dp(6), 0, 0);
        doneCol.addView(doneLbl);

        actions.addView(locCol);
        actions.addView(doneCol, doneLp);
        box.addView(actions);

        // Clicks
        locCol.setOnClickListener(v -> {
            Intent i = new Intent(this, HelperOrderMapActivity.class);
            i.putExtra("title", address == null ? "LOCATION" : address.toUpperCase(Locale.ROOT));
            i.putExtra("lat", lat == null ? 0 : lat);
            i.putExtra("lng", lng == null ? 0 : lng);
            startActivity(i);
        });

        doneCol.setOnClickListener(v -> {
            db.collection("orders").document(orderId)
                    .update("status", "completed", "completedAt", FieldValue.serverTimestamp())
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Marked completed", Toast.LENGTH_SHORT).show();
                        d.dismiss();
                        // refresh list
                        loadOrders();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });

        d.setContentView(box);
        d.show();
    }
}
