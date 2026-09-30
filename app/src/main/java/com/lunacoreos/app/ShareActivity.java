package com.lunacoreos.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.json.JSONObject;
import java.util.UUID;

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
                    saveToLinkbox(sharedText);
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

    private void saveToLinkbox(String sharedText) {
        Toast.makeText(this, "Saving to LunaCore...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                // Extract basic URL from shared text (sometimes apps send extra text alongside URL)
                String url = sharedText;
                String[] words = sharedText.split("\\s+");
                for (String word : words) {
                    if (word.startsWith("http://") || word.startsWith("https://")) {
                        url = word;
                        break;
                    }
                }

                JSONObject payload = new JSONObject();
                payload.put("id", UUID.randomUUID().toString());
                payload.put("url", url);
                payload.put("description", sharedText.equals(url) ? "" : sharedText);
                
                SupabaseClient client = new SupabaseClient(this);
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
