package com.lunacoreos.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MediaVaultGridActivity extends AppCompatActivity {

    private String collectionId;
    private String collectionPrefix;
    private SupabaseClient client;

    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    android.content.ClipData clipData = result.getData().getClipData();
                    if (clipData != null) {
                        // Multiple files selected
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            uploadFile(clipData.getItemAt(i).getUri());
                        }
                    } else {
                        // Single file selected
                        Uri uri = result.getData().getData();
                        if (uri != null) uploadFile(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_vault_grid);
        
        collectionId = getIntent().getStringExtra("COLLECTION_ID");
        collectionPrefix = getIntent().getStringExtra("COLLECTION_PREFIX");
        String collectionName = getIntent().getStringExtra("COLLECTION_NAME");
        client = new SupabaseClient(this);

        android.widget.TextView tvTitle = findViewById(R.id.tvTitle);
        if (collectionName != null) tvTitle.setText(collectionName);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.fabUpload).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            filePicker.launch(intent);
        });

        loadFiles();
    }

    private androidx.recyclerview.widget.RecyclerView rvMedia;
    private MediaAdapter adapter;
    private java.util.List<org.json.JSONObject> mediaList = new java.util.ArrayList<>();

    private void loadFiles() {
        new Thread(() -> {
            try {
                org.json.JSONArray files = client.getVaultFiles(collectionId);
                java.util.List<org.json.JSONObject> list = new java.util.ArrayList<>();
                for (int i = 0; i < files.length(); i++) {
                    list.add(files.getJSONObject(i));
                }
                runOnUiThread(() -> {
                    mediaList = list;
                    if (adapter == null) {
                        rvMedia = findViewById(R.id.rvMedia);
                        rvMedia.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
                        adapter = new MediaAdapter();
                        rvMedia.setAdapter(adapter);
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private class MediaAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<MediaAdapter.ViewHolder> {
        @androidx.annotation.NonNull
        @Override
        public ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_media, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ViewHolder holder, int position) {
            org.json.JSONObject fileObj = mediaList.get(position);
            String mime = fileObj.optString("mime_type", "");
            holder.ivPlayIcon.setVisibility(mime.startsWith("video/") ? android.view.View.VISIBLE : android.view.View.GONE);
            
            String r2Key = fileObj.optString("r2_key", "");
            String publicUrl = client.getR2PublicUrl(r2Key);
            
            if (publicUrl != null) {
                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(publicUrl)
                        .centerCrop()
                        .into(holder.ivThumbnail);
            }
        }

        @Override
        public int getItemCount() { return mediaList.size(); }

        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.ImageView ivThumbnail, ivPlayIcon;
            ViewHolder(android.view.View itemView) {
                super(itemView);
                ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
                ivPlayIcon = itemView.findViewById(R.id.ivPlayIcon);
            }
        }
    }

    private void uploadFile(Uri uri) {
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String filename = "upload";
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) mimeType = "application/octet-stream";

                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) filename = cursor.getString(nameIndex);
                    cursor.close();
                }

                String r2Key = collectionPrefix + System.currentTimeMillis() + "-" + filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                String putUrlStr = client.getR2PresignedPutUrl(r2Key, mimeType);

                java.net.URL putUrl = new java.net.URL(putUrlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) putUrl.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", mimeType);
                conn.setDoOutput(true);

                InputStream is = getContentResolver().openInputStream(uri);
                OutputStream os = conn.getOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalSize = 0;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    totalSize += bytesRead;
                }
                is.close();
                os.close();

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    org.json.JSONObject fileObj = new org.json.JSONObject();
                    fileObj.put("collection_id", collectionId);
                    fileObj.put("r2_key", r2Key);
                    fileObj.put("filename", filename);
                    fileObj.put("size_bytes", totalSize);
                    fileObj.put("mime_type", mimeType);
                    
                    client.saveVaultFile(fileObj);
                    
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Upload complete", Toast.LENGTH_SHORT).show();
                        loadFiles();
                    });
                } else {
                    throw new Exception("Upload failed: " + conn.getResponseCode());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
