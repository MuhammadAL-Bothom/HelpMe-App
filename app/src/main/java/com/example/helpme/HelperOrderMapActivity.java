package com.example.helpme;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/** Simple map screen that matches the third mock. */
public class HelperOrderMapActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String rawTitle = getIntent().getStringExtra("title");
        String title = rawTitle == null ? "LOCATION" : rawTitle.toUpperCase(Locale.ROOT);
        double lat = getIntent().getDoubleExtra("lat", 0);
        double lng = getIntent().getDoubleExtra("lng", 0);

        // Header
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(Color.WHITE);
        header.setPadding(dp(12), dp(18), dp(12), dp(18));
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(this);
        back.setImageResource(android.R.drawable.ic_media_previous);
        back.setColorFilter(Color.BLACK);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        backLp.rightMargin = dp(8);
        back.setLayoutParams(backLp);
        back.setOnClickListener(v -> finish());
        header.addView(back);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView app = tv("HELP ME APP", 22, "#D32F2F", true);
        app.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView role = tv("HELPER", 20, "#000000", true);
        role.setGravity(Gravity.CENTER_HORIZONTAL);
        titles.addView(app);
        titles.addView(role);

        header.addView(titles);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Grey location band
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setBackgroundColor(Color.parseColor("#7A7A7A"));
        band.setPadding(dp(12), dp(12), dp(12), dp(12));
        band.setGravity(Gravity.CENTER_VERTICAL);

        ImageView pin = new ImageView(this);
        pin.setImageResource(android.R.drawable.ic_menu_mylocation);
        pin.setColorFilter(Color.parseColor("#D4E157"));
        band.addView(pin, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView addr = tv(title, 16, "#FFFFFF", true);
        addr.setPadding(dp(12), 0, 0, 0);
        addr.setSingleLine(true);
        addr.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addr.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        band.addView(addr);

        root.addView(band, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Map (WebView Google Maps embed)
        WebView web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());

        String src;
        if (lat == 0 && lng == 0) {
            // Fallback to query string if coordinates are missing
            String q = android.net.Uri.encode(title);
            src = "https://maps.google.com/maps?q=" + q + "&z=16&output=embed";
        } else {
            src = "https://maps.google.com/maps?q=loc:" + lat + "," + lng + "&z=16&output=embed";
        }

        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'/></head>"
                + "<body style='margin:0'><iframe width='100%' height='100%' frameborder='0' style='border:0' src='" + src + "'></iframe></body></html>";
        web.loadDataWithBaseURL("https://maps.google.com", html, "text/html", "UTF-8", null);

        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private TextView tv(String text, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setAllCaps(true);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(sp);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return tv;
    }

    private int dp(int d) {
        float den = getResources().getDisplayMetrics().density;
        return Math.round(d * den);
    }
}
