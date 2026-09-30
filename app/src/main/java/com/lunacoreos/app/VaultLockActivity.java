package com.lunacoreos.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class VaultLockActivity extends AppCompatActivity {

    private EditText etMasterPassword;
    private TextView tvError;
    private TextView tvVaultTitle;
    private TextView tvVaultDesc;
    private Button btnUnlock;
    
    private boolean isFirstTime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Block screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_vault_lock);

        etMasterPassword = findViewById(R.id.etMasterPassword);
        tvError = findViewById(R.id.tvError);
        tvVaultTitle = findViewById(R.id.tvVaultTitle);
        tvVaultDesc = findViewById(R.id.tvVaultDesc);
        btnUnlock = findViewById(R.id.btnUnlock);

        isFirstTime = !CryptoService.hasCanary(this);

        // Allow callers to customize the title
        String lockTitle = getIntent().getStringExtra("LOCK_TITLE");
        
        if (isFirstTime) {
            tvVaultTitle.setText("Setup Vault");
            tvVaultDesc.setText("Create a Master Password for your Vault.\nDo not lose this, it cannot be recovered!");
            btnUnlock.setText("Create Vault");
        } else if (lockTitle != null) {
            tvVaultTitle.setText(lockTitle);
        }

        btnUnlock.setOnClickListener(v -> unlockVault());
    }

    private void unlockVault() {
        String masterPass = etMasterPassword.getText().toString();
        if (masterPass.isEmpty()) {
            tvError.setText("Password cannot be empty");
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        tvError.setVisibility(View.GONE);
        btnUnlock.setEnabled(false);
        btnUnlock.setText("Unlocking...");

        new Thread(() -> {
            try {
                // Derive key (takes time, runs in background)
                CryptoService.deriveKeyFromMaster(masterPass);
                
                if (isFirstTime) {
                    CryptoService.saveCanary(this);
                } else {
                    CryptoService.verifyCanary(this);
                }

                runOnUiThread(() -> {
                    setResult(Activity.RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    CryptoService.clearSessionKey();
                    tvError.setText(isFirstTime ? "Failed to setup vault" : "Incorrect master password.");
                    tvError.setVisibility(View.VISIBLE);
                    btnUnlock.setEnabled(true);
                    btnUnlock.setText(isFirstTime ? "Create Vault" : "Unlock");
                });
            }
        }).start();
    }
}
