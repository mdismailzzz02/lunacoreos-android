package com.lunacoreos.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.json.JSONObject;
import java.util.UUID;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class ShareActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    showAddLinkDialog(sharedText);
                } else {
                    finish();
                }
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

    private void showAddLinkDialog(String sharedText) {
        String extractedUrl = sharedText;
        String[] words = sharedText.split("\\s+");
        for (String word : words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                extractedUrl = word;
                break;
            }
        }
        
        String extractedDesc = sharedText.equals(extractedUrl) ? "" : sharedText;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_link, null);
        EditText etUrl = dialogView.findViewById(R.id.etUrl);
        EditText etTitle = dialogView.findViewById(R.id.etTitle);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etTags = dialogView.findViewById(R.id.etTags);

        etUrl.setText(extractedUrl);
        etDescription.setText(extractedDesc);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Save to LunaCore")
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Save", (dialog, which) -> {
                    String finalUrl = etUrl.getText().toString().trim();
                    if (finalUrl.isEmpty()) {
                        Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    saveLink(finalUrl, etTitle.getText().toString().trim(), etDescription.getText().toString().trim(), etTags.getText().toString().trim());
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show();
    }

    private void saveLink(String url, String title, String description, String tags) {
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("id", UUID.randomUUID().toString());
                payload.put("url", url);
                if (!title.isEmpty()) payload.put("title", title);
                if (!description.isEmpty()) payload.put("description", description);
                if (!tags.isEmpty()) payload.put("tags", tags);

                SupabaseClient client = new SupabaseClient(this);
                client.refreshSession();
                client.saveLinkboxEntry(payload);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved to Linkbox!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }
}
