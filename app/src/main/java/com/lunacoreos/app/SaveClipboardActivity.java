package com.lunacoreos.app;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SaveClipboardActivity extends AppCompatActivity {
    
    private String textToSave;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_clipboard);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            textToSave = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else {
            // Coming from Quick Settings Tile or elsewhere
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (text != null) {
                    textToSave = text.toString();
                }
            }
        }

        if (textToSave == null || textToSave.trim().isEmpty()) {
            Toast.makeText(this, "No text found to save", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView tvClipPreview = findViewById(R.id.tvClipPreview);
        tvClipPreview.setText(textToSave);

        Button btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> finish());

        Switch switchPrivate = findViewById(R.id.switchPrivate);
        if (intent != null && intent.hasExtra("IS_SECRET_MODE")) {
            switchPrivate.setChecked(intent.getBooleanExtra("IS_SECRET_MODE", false));
        }
        
        Button btnSave = findViewById(R.id.btnSave);

        btnSave.setOnClickListener(v -> {
            boolean isSecret = switchPrivate.isChecked();
            btnSave.setEnabled(false);
            btnSave.setText("Saving...");
            
            new Thread(() -> {
                try {
                    SupabaseClient client = new SupabaseClient(this);
                    client.saveClipboardText(textToSave, isSecret);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Saved to Lunacore Clipboard", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        btnSave.setEnabled(true);
                        btnSave.setText("Save");
                    });
                }
            }).start();
        });
    }
}
