package com.lunacoreos.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvStatus, tvSettings;
    
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("LunaCorePrefs", Context.MODE_PRIVATE);
        
        // Auto-login if token exists
        if (!prefs.getString("authToken", "").isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvStatus = findViewById(R.id.tvStatus);
        tvSettings = findViewById(R.id.tvSettings);

        btnLogin.setOnClickListener(v -> attemptLogin());
        
        tvSettings.setOnClickListener(v -> showConfigDialog());
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        String url = prefs.getString("supabaseUrl", "");
        String key = prefs.getString("supabaseKey", "");

        if (url.isEmpty() || key.isEmpty()) {
            tvStatus.setText("Backend not configured. Tap 'Configure Backend' first.");
            tvStatus.setVisibility(View.VISIBLE);
            return;
        }

        if (email.isEmpty() || password.isEmpty()) {
            tvStatus.setText("Enter email and password.");
            tvStatus.setVisibility(View.VISIBLE);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Signing in...");
        tvStatus.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                SupabaseClient client = new SupabaseClient(url, key);
                JSONObject loginJson = client.login(email, password);
                String token = loginJson.getString("access_token");
                String refreshToken = loginJson.optString("refresh_token", "");
                
                prefs.edit()
                     .putString("authToken", token)
                     .putString("refreshToken", refreshToken)
                     .apply();
                
                mainHandler.post(() -> {
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText(e.getMessage());
                    tvStatus.setVisibility(View.VISIBLE);
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Sign In");
                });
            }
        });
    }

    private void showConfigDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_config, null);
        EditText etUrl = view.findViewById(R.id.etUrl);
        EditText etKey = view.findViewById(R.id.etKey);
        
        etUrl.setText(prefs.getString("supabaseUrl", ""));
        etKey.setText(prefs.getString("supabaseKey", ""));

        new AlertDialog.Builder(this)
            .setTitle("Configure Backend")
            .setView(view)
            .setPositiveButton("Save", (dialog, which) -> {
                prefs.edit()
                    .putString("supabaseUrl", etUrl.getText().toString().trim())
                    .putString("supabaseKey", etKey.getText().toString().trim())
                    .apply();
                tvStatus.setVisibility(View.GONE);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
