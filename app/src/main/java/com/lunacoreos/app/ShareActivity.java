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
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ShareActivity extends AppCompatActivity {

    private boolean actionSelected = false;

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
                    showPicker(sharedText);
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

    private void showPicker(String sharedText) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Save to LunaCore");
        builder.setItems(new String[]{"📦 Save to Linkbox", "📦 Save to Secret Linkbox", "📋 Save to Clipboard", "🔒 Save to Secret Clipboard"}, (dialog, which) -> {
            actionSelected = true;
            if (which == 0) {
                showAddLinkDialog(sharedText, false);
            } else if (which == 1) {
                showAddLinkDialog(sharedText, true);
            } else if (which == 2) {
                saveToClipboard(sharedText, false);
            } else if (which == 3) {
                saveToClipboard(sharedText, true);
            }
        });
        builder.setOnDismissListener(dialog -> {
            if (!actionSelected) finish();
        });
        builder.show();
    }

    private void saveToClipboard(String text, boolean isSecret) {
        Toast.makeText(this, "Saving to Clipboard...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                SupabaseClient client = new SupabaseClient(this);
                client.refreshSession();
                client.saveClipboardText(text, isSecret);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved to Clipboard!", Toast.LENGTH_SHORT).show();
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

    private void showAddLinkDialog(String sharedText, boolean isSecret) {
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

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        if (tvDialogTitle != null) {
            tvDialogTitle.setText("Save to LunaCore");
        }

        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        bottomSheetDialog.setContentView(dialogView);
        ((View) dialogView.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);
        bottomSheetDialog.setCancelable(true);

        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String finalUrl = etUrl.getText().toString().trim();
            if (finalUrl.isEmpty()) {
                Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show();
                bottomSheetDialog.dismiss();
                finish();
                return;
            }
            saveLink(finalUrl, etTitle.getText().toString().trim(), etDescription.getText().toString().trim(), etTags.getText().toString().trim(), isSecret);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.setOnDismissListener(dialog -> finish());
        bottomSheetDialog.show();
    }

    private void saveLink(String url, String title, String description, String rawTags, boolean isSecret) {
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String tags = rawTags;
                if (isSecret && !tags.contains("__secret__")) {
                    tags = tags.isEmpty() ? "__secret__" : tags + ", __secret__";
                }
                
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
