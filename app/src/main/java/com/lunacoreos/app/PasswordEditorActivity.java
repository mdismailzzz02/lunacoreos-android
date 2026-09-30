package com.lunacoreos.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.UUID;

public class PasswordEditorActivity extends AppCompatActivity {

    private EditText etSiteName, etUsername, etPassword, etCategory;
    private TextView tvStrength;
    private Button btnSave, btnDelete;
    private ImageButton btnBack;

    private String existingId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Block screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_password_editor);

        etSiteName = findViewById(R.id.etSiteName);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etCategory = findViewById(R.id.etCategory);
        tvStrength = findViewById(R.id.tvStrength);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> savePassword());
        btnDelete.setOnClickListener(v -> deletePassword());

        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String strength = CryptoService.scorePasswordStrength(s.toString());
                tvStrength.setText("Strength: " + strength.substring(0, 1).toUpperCase() + strength.substring(1));
                if (strength.equals("weak")) tvStrength.setTextColor(getResources().getColor(R.color.danger, null));
                else if (strength.equals("fair")) tvStrength.setTextColor(getResources().getColor(android.R.color.holo_orange_light, null));
                else tvStrength.setTextColor(getResources().getColor(R.color.accent_green, null));
            }
        });

        String data = getIntent().getStringExtra("password_data");
        if (data != null) {
            try {
                JSONObject json = new JSONObject(data);
                existingId = json.getString("id");
                etSiteName.setText(json.optString("site_name"));
                etUsername.setText(json.optString("username"));
                etCategory.setText(json.optString("category", "General"));
                
                String enc = json.getString("enc_password");
                String iv = json.getString("enc_iv");
                String plain = CryptoService.decryptPassword(enc, iv);
                etPassword.setText(plain);

                btnDelete.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to decrypt password", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            btnDelete.setVisibility(View.GONE);
        }
    }

    private void savePassword() {
        String site = etSiteName.getText().toString();
        String user = etUsername.getText().toString();
        String pass = etPassword.getText().toString();
        String cat = etCategory.getText().toString();

        if (site.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Site name and password required", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        new Thread(() -> {
            try {
                CryptoService.EncryptedData enc = CryptoService.encryptPassword(pass);
                
                JSONObject payload = new JSONObject();
                payload.put("id", existingId != null ? existingId : UUID.randomUUID().toString());
                payload.put("site_name", site);
                payload.put("username", user);
                payload.put("enc_password", enc.encPassword);
                payload.put("enc_iv", enc.encIv);
                payload.put("category", cat.isEmpty() ? "General" : cat);
                payload.put("strength", CryptoService.scorePasswordStrength(pass));
                // URL and Notes not currently in UI for brevity but can be added

                SupabaseClient client = new SupabaseClient(this);
                client.savePassword(payload);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                });
            }
        }).start();
    }

    private void deletePassword() {
        if (existingId == null) return;
        
        btnDelete.setEnabled(false);
        btnDelete.setText("Deleting...");

        new Thread(() -> {
            try {
                SupabaseClient client = new SupabaseClient(this);
                client.deletePassword(existingId);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                    btnDelete.setEnabled(true);
                    btnDelete.setText("Delete Entry");
                });
            }
        }).start();
    }
}
