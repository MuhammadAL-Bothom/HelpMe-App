package com.example.helpme;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OrdersActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView recycler;
    private TextView title;
    private TextView empty;
    private OrdersHistoryAdapter adapter;

    private String role = "user";
    private ListenerRegistration sub;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);

        role = getIntent().getStringExtra("role");
        if (role == null) role = "user";

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        title    = findViewById(R.id.ordersTitle);
        recycler = findViewById(R.id.ordersList);
        empty    = findViewById(R.id.ordersEmpty);

        title.setText("ORDERS");
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrdersHistoryAdapter();
        recycler.setAdapter(adapter);

        load();
    }

    @Override protected void onDestroy() {
        if (sub != null) sub.remove();
        super.onDestroy();
    }

    private void load() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) { finish(); return; }

        // Completed orders only (helper sees their completed jobs; user sees their completed orders)
        Query q;
        if ("helper".equalsIgnoreCase(role)) {
            q = db.collection("orders")
                    .whereEqualTo("helperId", u.getUid())
                    .whereEqualTo("status", "completed")
                    .orderBy("completedAt", Query.Direction.DESCENDING);
        } else {
            q = db.collection("orders")
                    .whereEqualTo("userId", u.getUid())
                    .whereEqualTo("status", "completed")
                    .orderBy("completedAt", Query.Direction.DESCENDING);
        }

        if (sub != null) sub.remove();
        // Real-time listener
        sub = q.addSnapshotListener((snap, err) -> {
            if (err != null || snap == null) return;

            ArrayList<DocumentSnapshot> docs = new ArrayList<>(snap.getDocuments());
            adapter.submit(docs, role);

            boolean has = !docs.isEmpty();
            empty.setVisibility(has ? View.GONE : View.VISIBLE);
            recycler.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        });
    }

    // ---------- adapter ----------
    private class OrdersHistoryAdapter extends RecyclerView.Adapter<OrdersHistoryAdapter.VH> {

        private final ArrayList<DocumentSnapshot> data = new ArrayList<>();
        private String role = "user";

        // tiny caches so we only read each /users/{uid} once
        private final Map<String, String> nameCache = new HashMap<>();
        private final Map<String, String> professionCache = new HashMap<>();
        private final Set<String> inFlight = new HashSet<>();

        void submit(ArrayList<DocumentSnapshot> d, String role) {
            this.role = (role == null) ? "user" : role;
            data.clear(); data.addAll(d);
            notifyDataSetChanged();

            // Preload names/professions that might be missing on the order docs
            for (DocumentSnapshot doc : d) {
                if ("helper".equalsIgnoreCase(this.role)) {
                    maybeFetchUser(doc.getString("userId"));     // show customer name to helper
                } else {
                    maybeFetchHelper(doc.getString("helperId")); // show helper name/profession to user
                }
            }
        }

        private void maybeFetchHelper(@Nullable String helperId) {
            if (TextUtils.isEmpty(helperId)) return;
            if (nameCache.containsKey(helperId) && professionCache.containsKey(helperId)) return;
            if (!inFlight.add(helperId)) return;

            db.collection("users").document(helperId).get()
                    .addOnSuccessListener(p -> {
                        String nm = prefer(
                                p.getString("name"),
                                p.getString("displayName"),
                                p.getString("fullName"));
                        String prof = p.getString("profession");
                        if (!TextUtils.isEmpty(nm)) nameCache.put(helperId, nm);
                        if (!TextUtils.isEmpty(prof)) professionCache.put(helperId, prof);
                        inFlight.remove(helperId);
                        notifyForUid(helperId);
                    })
                    .addOnFailureListener(e -> inFlight.remove(helperId));
        }

        private void maybeFetchUser(@Nullable String userId) {
            if (TextUtils.isEmpty(userId)) return;
            if (nameCache.containsKey(userId)) return;
            if (!inFlight.add(userId)) return;

            db.collection("users").document(userId).get()
                    .addOnSuccessListener(p -> {
                        String nm = prefer(
                                p.getString("name"),
                                p.getString("displayName"),
                                p.getString("fullName"));
                        if (!TextUtils.isEmpty(nm)) nameCache.put(userId, nm);
                        inFlight.remove(userId);
                        notifyForUid(userId);
                    })
                    .addOnFailureListener(e -> inFlight.remove(userId));
        }

        private void notifyForUid(String uid) {
            for (int i = 0; i < data.size(); i++) {
                DocumentSnapshot d = data.get(i);
                String key = "helper".equalsIgnoreCase(role) ? d.getString("userId") : d.getString("helperId");
                if (uid.equals(key)) notifyItemChanged(i);
            }
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name, profession;
            LinearLayout starsContainer;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.rowName);
                profession = v.findViewById(R.id.rowProfession);
                starsContainer = v.findViewById(R.id.rowStars);
            }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int viewType) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.row_order_history, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            DocumentSnapshot d = data.get(pos);

            // Who to display on the row?
            if ("helper".equalsIgnoreCase(role)) {
                String userId = d.getString("userId");
                String displayName = upperOr(
                        prefer(d.getString("userName"), nameCache.get(userId)),
                        "CUSTOMER");
                h.name.setText(displayName);
                h.profession.setText("COMPLETED ORDER");
            } else {
                String helperId = d.getString("helperId");
                String displayName = upperOr(
                        prefer(d.getString("helperName"), nameCache.get(helperId)),
                        "YOUR HELPER");
                h.name.setText(displayName);

                String displayProf = upperOr(
                        prefer(d.getString("helperProfession"), professionCache.get(helperId)),
                        "SERVICE");
                h.profession.setText(displayProf);
            }

            // === stars strictly from Firestore userRating ===
            int stars = getUserRatingSafe(d);  // 0 if missing
            if (stars <= 0) {
                h.starsContainer.setVisibility(View.GONE);
            } else {
                h.starsContainer.setVisibility(View.VISIBLE);
                renderStarsExactly(h.starsContainer, stars); // show exactly N yellow stars
            }

            // Accessibility / ordering context
            Timestamp doneAt = d.getTimestamp("completedAt");
            h.itemView.setContentDescription(doneAt == null ? "" : doneAt.toDate().toString());
        }

        @Override public int getItemCount() { return data.size(); }

        // ---- helpers ----
        private String prefer(String... vals) {
            if (vals == null) return null;
            for (String v : vals) if (!TextUtils.isEmpty(v)) return v;
            return null;
        }
        private String upperOr(String v, String fallback) {
            if (TextUtils.isEmpty(v)) v = fallback;
            return v.toUpperCase(Locale.ROOT);
        }
        private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

        private int dp(View v, int d) {
            float den = v.getResources().getDisplayMetrics().density;
            return Math.round(d * den);
        }

        /** Reads userRating as a number safely (handles Long/Double/String). */
        private int getUserRatingSafe(DocumentSnapshot d) {
            Object raw = d.get("userRating");
            if (raw instanceof Number) {
                return clamp(((Number) raw).intValue(), 1, 5);
            }
            if (raw instanceof String) {
                try { return clamp(Integer.parseInt((String) raw), 1, 5); }
                catch (Exception ignore) {}
            }
            return 0; // not rated yet
        }

        /** Draw EXACTLY 'count' yellow stars (no grey/off stars). */
        private void renderStarsExactly(LinearLayout container, int count) {
            container.removeAllViews();
            int n = clamp(count, 1, 5);
            for (int i = 0; i < n; i++) {
                ImageView iv = new ImageView(container.getContext());
                int sz = dp(container, 22);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
                if (i > 0) lp.leftMargin = dp(container, 6);
                iv.setLayoutParams(lp);
                iv.setImageResource(android.R.drawable.btn_star_big_on);
                iv.setColorFilter(Color.parseColor("#D4E157")); // yellow
                container.addView(iv);
            }
        }
    }
}
