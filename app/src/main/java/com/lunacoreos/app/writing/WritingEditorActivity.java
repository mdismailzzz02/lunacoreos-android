package com.lunacoreos.app.writing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lunacoreos.app.R;
import com.lunacoreos.app.SupabaseClient;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WritingEditorActivity extends AppCompatActivity {

    private EditText etTitle, etTags, etContent;
    private TextView tvSaveStatus, tvWordCount;
    private Button btnDelete, btnSaveClose;
    private ImageButton btnBack;

    private String currentId;
    private String currentMode;
    private SupabaseClient supabase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;
    private final long AUTO_SAVE_DELAY = 1500;
    
    private boolean isSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_writing_editor);

        etTitle = findViewById(R.id.etTitle);
        etTags = findViewById(R.id.etTags);
        etContent = findViewById(R.id.etContent);
        tvSaveStatus = findViewById(R.id.tvSaveStatus);
        tvWordCount = findViewById(R.id.tvWordCount);
        btnDelete = findViewById(R.id.btnDelete);
        btnSaveClose = findViewById(R.id.btnSaveClose);
        btnBack = findViewById(R.id.btnBack);

        SharedPreferences prefs = getSharedPreferences("LunaCorePrefs", Context.MODE_PRIVATE);
        String url = prefs.getString("supabaseUrl", "");
        String key = prefs.getString("supabaseKey", "");
        String token = prefs.getString("authToken", "");
        
        supabase = new SupabaseClient(url, key);
        supabase.setAuthToken(token);

        currentId = getIntent().getStringExtra("id");
        currentMode = getIntent().getStringExtra("mode");
        
        if ("new".equals(currentId)) {
            currentId = String.valueOf(System.currentTimeMillis());
            btnDelete.setVisibility(View.GONE);
        } else {
            etTitle.setText(getIntent().getStringExtra("title"));
            etTags.setText(getIntent().getStringExtra("tags"));
            etContent.setText(getIntent().getStringExtra("content"));
            updateWordCount();
        }

        setupListeners();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        btnSaveClose.setOnClickListener(v -> {
            saveDraft(true);
        });

        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete Draft")
                .setMessage("Are you sure you want to delete this draft?")
                .setPositiveButton("Delete", (dialog, which) -> deleteDraft())
                .setNegativeButton("Cancel", null)
                .show();
        });

        TextWatcher autoSaveWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (saveRunnable != null) {
                    mainHandler.removeCallbacks(saveRunnable);
                }
                tvSaveStatus.setText("Typing...");
                saveRunnable = () -> saveDraft(false);
                mainHandler.postDelayed(saveRunnable, AUTO_SAVE_DELAY);
                updateWordCount();
            }
        };

        etTitle.addTextChangedListener(autoSaveWatcher);
        etTags.addTextChangedListener(autoSaveWatcher);
        etContent.addTextChangedListener(autoSaveWatcher);
    }

    private void updateWordCount() {
        String text = etContent.getText().toString().trim();
        if (text.isEmpty()) {
            tvWordCount.setText("0 words");
        } else {
            int count = text.split("\\s+").length;
            tvWordCount.setText(count + " words");
        }
    }

    private void saveDraft(boolean closeOnSuccess) {
        if (etTitle.getText().toString().trim().isEmpty()) return;

        tvSaveStatus.setText("Saving...");
        
        final String title = etTitle.getText().toString().trim();
        final String tags = etTags.getText().toString().trim();
        final String content = etContent.getText().toString().trim();
        final String wordCount = tvWordCount.getText().toString().replace(" words", "");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String now = sdf.format(new Date());

        executor.execute(() -> {
            try {
                JSONObject draft = new JSONObject();
                draft.put("id", currentId);
                draft.put("title", title);
                draft.put("tags", tags);
                draft.put("content", content);
                draft.put("word_count", wordCount);
                draft.put("mode", currentMode);
                draft.put("updatedAt", now);
                // We omit created_at here so Supabase handles it or we preserve it. 
                // In a perfect world, we'd fetch the old created_at, but UPSERT handles it.
                
                supabase.saveWriting(draft);
                
                mainHandler.post(() -> {
                    tvSaveStatus.setText("Saved");
                    btnDelete.setVisibility(View.VISIBLE);
                    if (closeOnSuccess) {
                        finish();
                    } else {
                        mainHandler.postDelayed(() -> {
                            if (tvSaveStatus.getText().toString().equals("Saved")) {
                                tvSaveStatus.setText("");
                            }
                        }, 2000);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> tvSaveStatus.setText("Failed to save"));
            }
        });
    }

    private void deleteDraft() {
        if (saveRunnable != null) {
            mainHandler.removeCallbacks(saveRunnable);
        }
        tvSaveStatus.setText("Deleting...");
        executor.execute(() -> {
            try {
                supabase.deleteWriting(currentId);
                mainHandler.post(this::finish);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvSaveStatus.setText("Failed to delete");
                    Toast.makeText(this, "Error deleting draft", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        if (saveRunnable != null) {
            mainHandler.removeCallbacks(saveRunnable);
        }
        super.onDestroy();
    }
}
