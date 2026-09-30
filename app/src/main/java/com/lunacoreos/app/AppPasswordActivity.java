package com.lunacoreos.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.security.MessageDigest;

public class AppPasswordActivity extends AppCompatActivity {

    private EditText etPassword;
    private TextView tvError;
    private TextView tvTitle;
    private TextView tvDesc;
    private Button btnUnlock;
    
    private String lockId;
    private String lockTitle;
    private JSONObject storedRecord;
    private boolean isSetMode = false;
    private SupabaseClient client;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_vault_lock); // Reuse the same layout

        lockId = getIntent().getStringExtra("LOCK_ID");
        lockTitle = getIntent().getStringExtra("LOCK_TITLE");
        client = new SupabaseClient(this);

        etPassword = findViewById(R.id.etMasterPassword);
        etPassword.setHint("Vault Password");
        tvError = findViewById(R.id.tvError);
        tvTitle = findViewById(R.id.tvVaultTitle);
        tvDesc = findViewById(R.id.tvVaultDesc);
        btnUnlock = findViewById(R.id.btnUnlock);

        if (lockTitle != null) tvTitle.setText(lockTitle);
        tvDesc.setText("Loading secure context...");
        etPassword.setVisibility(View.GONE);
        btnUnlock.setEnabled(false);

        checkStatus();
    }

    private void checkStatus() {
        new Thread(() -> {
            try {
                storedRecord = client.getAppPasswordV2(lockId);
                runOnUiThread(() -> {
                    etPassword.setVisibility(View.VISIBLE);
                    btnUnlock.setEnabled(true);
                    if (storedRecord != null && storedRecord.has("hash")) {
                        isSetMode = false;
                        tvDesc.setText("Enter key to access " + lockTitle);
                        btnUnlock.setText("Unlock");
                    } else {
                        isSetMode = true;
                        tvDesc.setText("Set a new key for " + lockTitle);
                        btnUnlock.setText("Set Key");
                    }
                    btnUnlock.setOnClickListener(v -> submit());
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvError.setText("Failed to load secure context");
                    tvError.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void submit() {
        String pwd = etPassword.getText().toString();
        if (pwd.length() < 6) {
            tvError.setText("Key must be at least 6 characters.");
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        tvError.setVisibility(View.GONE);
        btnUnlock.setEnabled(false);
        btnUnlock.setText(isSetMode ? "Setting..." : "Verifying...");

        new Thread(() -> {
            try {
                if (isSetMode) {
                    String salt = generateSalt();
                    String hash = sha256(salt + pwd);
                    client.setAppPasswordV2(lockId, lockTitle, salt, hash);
                    runOnUiThread(() -> {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("VAULT_MODE", getIntent().getStringExtra("VAULT_MODE"));
                        setResult(Activity.RESULT_OK, resultIntent);
                        finish();
                    });
                } else {
                    String hash = sha256(storedRecord.getString("salt") + pwd);
                    if (hash.equals(storedRecord.getString("hash"))) {
                        runOnUiThread(() -> {
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("VAULT_MODE", getIntent().getStringExtra("VAULT_MODE"));
                            setResult(Activity.RESULT_OK, resultIntent);
                            finish();
                        });
                    } else {
                        throw new Exception("Invalid key");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvError.setText("Access Denied");
                    tvError.setVisibility(View.VISIBLE);
                    btnUnlock.setEnabled(true);
                    btnUnlock.setText(isSetMode ? "Set Key" : "Unlock");
                });
            }
        }).start();
    }

    private String generateSalt() {
        byte[] arr = new byte[16];
        new java.security.SecureRandom().nextBytes(arr);
        StringBuilder sb = new StringBuilder();
        for (byte b : arr) sb.append(String.format("%02x", 0xFF & b));
        return sb.toString();
    }

    private String sha256(String message) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(message.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) hexString.append(String.format("%02x", 0xFF & b));
        return hexString.toString();
    }
}
