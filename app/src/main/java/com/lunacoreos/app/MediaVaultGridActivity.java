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
    
    // Cache for known broken thumbnail URLs to prevent 404 network delays on scroll
    private final java.util.Set<String> brokenThumbUrls = new java.util.HashSet<>();
    
    private final java.util.concurrent.atomic.AtomicInteger uploadsPending = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger uploadsCompleted = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicLong totalBytesToUpload = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalBytesUploaded = new java.util.concurrent.atomic.AtomicLong(0);

    private void updateUploadUI() {
        int pending = uploadsPending.get();
        int completed = uploadsCompleted.get();
        int totalFiles = pending + completed;
        long bytesTotal = totalBytesToUpload.get();
        long bytesDone = totalBytesUploaded.get();
        
        android.widget.LinearLayout container = findViewById(R.id.uploadProgressContainer);
        android.widget.TextView tvProgress = findViewById(R.id.tvUploadProgress);
        android.widget.ProgressBar pb = findViewById(R.id.pbUpload);
        
        if (pending == 0) {
            container.setVisibility(android.view.View.GONE);
            uploadsCompleted.set(0);
            totalBytesToUpload.set(0);
            totalBytesUploaded.set(0);
            loadFiles();
        } else {
            container.setVisibility(android.view.View.VISIBLE);
            if (totalFiles > 1) {
                tvProgress.setText("Uploading " + completed + " of " + totalFiles + " files...");
            } else {
                tvProgress.setText("Uploading...");
            }
            pb.setIndeterminate(false);
            if (bytesTotal > 0) {
                pb.setMax(100);
                pb.setProgress((int) ((bytesDone * 100) / bytesTotal));
            } else {
                pb.setIndeterminate(true);
            }
        }
    }

    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    android.content.ClipData clipData = result.getData().getClipData();
                    if (clipData != null) {
                        // Multiple files selected
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            uploadsPending.incrementAndGet();
                            uploadFile(clipData.getItemAt(i).getUri());
                        }
                        updateUploadUI();
                    } else {
                        // Single file selected
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            uploadsPending.incrementAndGet();
                            uploadFile(uri);
                            updateUploadUI();
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_vault_grid);
        
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_vault);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_vault) return true;
            if (item.getItemId() == R.id.nav_more) {
                Intent i = new Intent(MediaVaultGridActivity.this, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                return false;
            }
            
            Intent i = new Intent(MediaVaultGridActivity.this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("TARGET_TAB", item.getItemId());
            startActivity(i);
            finish();
            return true;
        });
        
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

        android.widget.LinearLayout btnFavorites = findViewById(R.id.btnFavorites);
        android.widget.ImageView ivHeart = findViewById(R.id.ivHeart);
        android.widget.TextView tvFavorites = findViewById(R.id.tvFavorites);
        
        findViewById(R.id.btnAddFolder).setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(this);
            input.setHint("Folder Name");
            input.setTextColor(0xFF000000); // Black text for light dialog
            input.setHintTextColor(0xFF888888); // Gray hint
            
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("New Folder")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        new Thread(() -> {
                            try {
                                org.json.JSONObject folder = new org.json.JSONObject();
                                folder.put("name", name);
                                folder.put("type", "gallery");
                                folder.put("key_prefix", collectionPrefix + name.replaceAll("[^a-zA-Z0-9._-]", "_") + "/");
                                folder.put("parent_id", collectionId);
                                folder.put("is_hidden", false);
                                folder.put("is_secret", false);
                                client.createVaultCollection(folder);
                                runOnUiThread(() -> {
                                    android.widget.Toast.makeText(MediaVaultGridActivity.this, "Folder created", android.widget.Toast.LENGTH_SHORT).show();
                                    loadFiles();
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                runOnUiThread(() -> android.widget.Toast.makeText(MediaVaultGridActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        btnFavorites.setOnClickListener(v -> {
            showFavorites = !showFavorites;
            ivHeart.setColorFilter(showFavorites ? 0xFFEC4899 : getResources().getColor(R.color.text_secondary));
            tvFavorites.setTextColor(showFavorites ? 0xFFEC4899 : getResources().getColor(R.color.text_secondary));
            
            // Reload grid
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                mediaList.removeIf(item -> "file".equals(item.optString("item_type")));
            } else {
                java.util.Iterator<org.json.JSONObject> it = mediaList.iterator();
                while(it.hasNext()){
                    if("file".equals(it.next().optString("item_type"))) it.remove();
                }
            }
            if (adapter != null) adapter.notifyDataSetChanged();
            
            currentOffset = 0;
            hasMore = true;
            isLoading = false;
            fetchNextPage();
        });

        loadFiles();
    }

    private androidx.recyclerview.widget.RecyclerView rvMedia;
    private MediaAdapter adapter;
    private java.util.List<org.json.JSONObject> mediaList = new java.util.ArrayList<>();
    
    private boolean isLoading = false;
    private boolean hasMore = true;
    private int currentOffset = 0;
    private final int PAGE_LIMIT = 21;
    
    private boolean showFavorites = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (MediaViewerActivity.deletedFileIds != null && !MediaViewerActivity.deletedFileIds.isEmpty()) {
            boolean changed = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                changed = mediaList.removeIf(item -> "file".equals(item.optString("item_type")) && MediaViewerActivity.deletedFileIds.contains(item.optString("id")));
            } else {
                java.util.Iterator<org.json.JSONObject> it = mediaList.iterator();
                while(it.hasNext()){
                    org.json.JSONObject item = it.next();
                    if("file".equals(item.optString("item_type")) && MediaViewerActivity.deletedFileIds.contains(item.optString("id"))) {
                        it.remove();
                        changed = true;
                    }
                }
            }
            if (changed && adapter != null) {
                adapter.notifyDataSetChanged();
            }
            MediaViewerActivity.deletedFileIds.clear();
        }
    }

    private void loadFiles() {
        // Reset state
        currentOffset = 0;
        hasMore = true;
        isLoading = false;
        
        mediaList.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        
        new Thread(() -> {
            try {
                java.util.List<org.json.JSONObject> newFolders = new java.util.ArrayList<>();
                
                // Fetch and show folders first
                try {
                    org.json.JSONArray subcollections = client.getVaultSubCollections(collectionId);
                    for (int i = 0; i < subcollections.length(); i++) {
                        org.json.JSONObject folder = subcollections.getJSONObject(i);
                        folder.put("item_type", "folder");
                        newFolders.add(folder);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    final String msg = e.getMessage();
                    runOnUiThread(() -> android.widget.Toast.makeText(MediaVaultGridActivity.this, "Folder err: " + msg, android.widget.Toast.LENGTH_LONG).show());
                }
                
                runOnUiThread(() -> {
                    // Only add folders if we are still at offset 0 (no files loaded yet)
                    if (currentOffset == 0) {
                        mediaList.addAll(0, newFolders);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    }
                });
                
                runOnUiThread(() -> {
                    if (adapter == null) {
                        rvMedia = findViewById(R.id.rvMedia);
                        androidx.recyclerview.widget.GridLayoutManager layoutManager = new androidx.recyclerview.widget.GridLayoutManager(this, 3);
                        layoutManager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                            @Override
                            public int getSpanSize(int position) {
                                if (position == mediaList.size()) return 3; // Load More button spans full width
                                if ("folder".equals(mediaList.get(position).optString("item_type"))) {
                                    return 3;
                                }
                                return 1;
                            }
                        });
                        rvMedia.setLayoutManager(layoutManager);
                        
                        adapter = new MediaAdapter();
                        rvMedia.setAdapter(adapter);
                        
                        rvMedia.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                            @Override
                            public void onScrolled(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                                super.onScrolled(recyclerView, dx, dy);
                                if (dy > 0) {
                                    int visibleItemCount = layoutManager.getChildCount();
                                    int totalItemCount = layoutManager.getItemCount();
                                    int pastVisibleItems = layoutManager.findFirstVisibleItemPosition();
                                    
                                    if (!isLoading && hasMore) {
                                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 10) {
                                            fetchNextPage();
                                        }
                                    }
                                }
                            }
                        });
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                });

                fetchNextPage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchNextPage() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        new Thread(() -> {
            try {
                org.json.JSONArray files = client.getVaultFiles(collectionId, PAGE_LIMIT, currentOffset, showFavorites);
                if (files.length() == 0) {
                    hasMore = false;
                    isLoading = false;
                    runOnUiThread(() -> {
                        if (adapter != null) adapter.notifyDataSetChanged();
                    });
                    return;
                }
                
                java.util.List<org.json.JSONObject> newFiles = new java.util.ArrayList<>();
                java.util.List<String> batchKeys = new java.util.ArrayList<>();
                
                for (int i = 0; i < files.length(); i++) {
                    org.json.JSONObject file = files.getJSONObject(i);
                    file.put("item_type", "file");
                    
                    String r2Key = file.optString("r2_key", "");
                    if (!r2Key.isEmpty()) batchKeys.add(r2Key);
                    
                    String thumbKey = file.optString("thumbnail_key", "");
                    if (!thumbKey.isEmpty() && !thumbKey.startsWith("data:image/")) {
                        batchKeys.add(thumbKey);
                    }
                    
                    newFiles.add(file);
                }

                if (!batchKeys.isEmpty()) {
                    try {
                        org.json.JSONObject batchUrls = client.getR2PresignedBatch(batchKeys);
                        for (org.json.JSONObject file : newFiles) {
                            String r2Key = file.optString("r2_key", "");
                            if (!r2Key.isEmpty() && batchUrls.has(r2Key)) {
                                file.put("signed_url", batchUrls.optString(r2Key));
                            }
                            String thumbKey = file.optString("thumbnail_key", "");
                            if (!thumbKey.isEmpty() && !thumbKey.startsWith("data:image/") && batchUrls.has(thumbKey)) {
                                file.put("thumbnail_signed_url", batchUrls.optString(thumbKey));
                            }
                        }
                        android.util.Log.d("MediaVault", "Batched " + batchKeys.size() + " presigned URLs successfully");
                    } catch (Exception e) {
                        android.util.Log.w("MediaVault", "Batch presign GET failed: " + e.getMessage());
                    }
                }
                
                runOnUiThread(() -> {
                    int startPosition = mediaList.size();
                    mediaList.addAll(newFiles);
                    currentOffset += PAGE_LIMIT;
                    isLoading = false;
                    if (files.length() < PAGE_LIMIT) {
                        hasMore = false;
                    }
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                isLoading = false;
                runOnUiThread(() -> {
                    if (adapter != null) adapter.notifyDataSetChanged();
                });
            }
        }).start();
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int i = (int) Math.floor(Math.log(bytes) / Math.log(1024));
        if (i >= units.length) i = units.length - 1;
        return String.format("%.1f %s", bytes / Math.pow(1024, i), units[i]);
    }

    private class MediaAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder> {
        private static final int TYPE_FOLDER = 0;
        private static final int TYPE_FILE = 1;
        private static final int TYPE_LOAD_MORE = 2;

        @Override
        public int getItemViewType(int position) {
            if (position == mediaList.size()) return TYPE_LOAD_MORE;
            return "folder".equals(mediaList.get(position).optString("item_type")) ? TYPE_FOLDER : TYPE_FILE;
        }

        @androidx.annotation.NonNull
        @Override
        public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            if (viewType == TYPE_LOAD_MORE) {
                android.widget.Button btn = new android.widget.Button(parent.getContext());
                btn.setText("Load More");
                btn.setTextColor(0xFFA78BFA); // #A78BFA
                btn.setBackgroundColor(0x1AA78BFA); // 10% opacity
                btn.setAllCaps(false);
                btn.setTypeface(null, android.graphics.Typeface.BOLD);
                androidx.recyclerview.widget.RecyclerView.LayoutParams lp = new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(32, 64, 32, 128);
                btn.setLayoutParams(lp);
                btn.setPadding(0, 32, 0, 32);
                return new LoadMoreViewHolder(btn);
            } else if (viewType == TYPE_FOLDER) {
                android.view.View view = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_collection, parent, false);
                return new FolderViewHolder(view);
            } else {
                android.view.View view = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_media, parent, false);
                return new FileViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_LOAD_MORE) {
                LoadMoreViewHolder lh = (LoadMoreViewHolder) holder;
                lh.btn.setText(isLoading ? "Loading..." : "Load More");
                lh.btn.setOnClickListener(v -> {
                    if (!isLoading && hasMore) fetchNextPage();
                });
                return;
            }

            org.json.JSONObject item = mediaList.get(position);
            
            if (getItemViewType(position) == TYPE_FOLDER) {
                FolderViewHolder fh = (FolderViewHolder) holder;
                fh.tvName.setText(item.optString("name", "Unnamed Folder"));
                String type = item.optString("type", "folder");
                int fileCount = item.optInt("file_count", 0);
                long sizeBytes = item.optLong("size_bytes", 0);
                String info = type.substring(0, 1).toUpperCase() + type.substring(1);
                if (fileCount > 0 || sizeBytes > 0) {
                    info += " · " + fileCount + " file" + (fileCount != 1 ? "s" : "")
                          + " · " + formatBytes(sizeBytes);
                }
                fh.tvType.setText(info);
                
                boolean isSecret = item.optBoolean("is_secret", false);
                fh.ivSecretLock.setVisibility(isSecret ? android.view.View.VISIBLE : android.view.View.GONE);

                fh.ivIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                
                fh.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(MediaVaultGridActivity.this, MediaVaultGridActivity.class);
                    intent.putExtra("COLLECTION_ID", item.optString("id"));
                    intent.putExtra("COLLECTION_PREFIX", item.optString("key_prefix"));
                    intent.putExtra("COLLECTION_NAME", item.optString("name"));
                    startActivity(intent);
                });

                fh.itemView.setOnLongClickListener(v -> {
                    new android.app.AlertDialog.Builder(MediaVaultGridActivity.this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                        .setTitle("Delete Folder")
                        .setMessage("Are you sure you want to delete '" + item.optString("name") + "'? All files inside will be moved to Trash.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            new Thread(() -> {
                                try {
                                    String id = item.optString("id");
                                    client.trashCollectionFiles(id);
                                    client.deleteVaultCollection(id);
                                    runOnUiThread(() -> {
                                        android.widget.Toast.makeText(MediaVaultGridActivity.this, "Folder deleted", android.widget.Toast.LENGTH_SHORT).show();
                                        loadFiles();
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(() -> android.widget.Toast.makeText(MediaVaultGridActivity.this, "Delete failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                                }
                            }).start();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
                });
            } else {
                FileViewHolder fh = (FileViewHolder) holder;
                String mime = item.optString("mime_type", "");
                String nameStr = item.optString("name", "");
                if (nameStr.isEmpty()) nameStr = item.optString("filename", "Unknown");
                fh.tvFileName.setText(nameStr);
                fh.tvFileName.setVisibility(android.view.View.VISIBLE);
                fh.ivPlayIcon.setVisibility(mime.startsWith("video/") ? android.view.View.VISIBLE : android.view.View.GONE);
                
                String r2Key = item.optString("r2_key", "");
                // Prefer pre-fetched signed URL, fall back to public URL
                String signedUrl = item.optString("signed_url", "");
                String publicUrl = signedUrl.isEmpty() ? client.getR2PublicUrl(r2Key) : signedUrl;

                android.util.Log.d("MediaVault", "Binding: using " + (signedUrl.isEmpty() ? "publicUrl" : "signedUrl") + " for " + r2Key);

                String thumb = item.optString("thumbnail_key", "");
                boolean thumbLoaded = false;
                
                boolean isMedia = mime.startsWith("image/") || mime.startsWith("video/");
                
                if (!isMedia) {
                    int iconRes = android.R.drawable.ic_menu_help;
                    String lowMime = mime.toLowerCase();
                    String name = item.optString("filename", "").toLowerCase();
                    if (name.isEmpty()) name = item.optString("name", "").toLowerCase();
                    
                    if (lowMime.contains("android.package-archive") || name.endsWith(".apk") || name.endsWith(".xapk") || name.endsWith(".exe") || name.endsWith(".msi") || lowMime.contains("msdownload")) {
                        iconRes = android.R.drawable.sym_def_app_icon; // App/Executable
                    } else if (lowMime.contains("pdf") || name.endsWith(".pdf")) {
                        iconRes = android.R.drawable.ic_menu_agenda; // Document/PDF
                    } else if (lowMime.contains("spreadsheet") || lowMime.contains("excel") || name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv")) {
                        iconRes = android.R.drawable.ic_menu_view; // Spreadsheet/Data
                    } else if (lowMime.contains("wordprocessing") || lowMime.contains("document") || name.endsWith(".doc") || name.endsWith(".docx")) {
                        iconRes = android.R.drawable.ic_menu_edit; // Text Document
                    } else if (lowMime.startsWith("text/") || lowMime.contains("javascript") || lowMime.contains("json") || lowMime.contains("html") || lowMime.contains("css") || lowMime.contains("xml") || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".py") || name.endsWith(".java")) {
                        iconRes = android.R.drawable.ic_menu_sort_by_size; // Code/Text
                    } else if (lowMime.contains("zip") || lowMime.contains("archive") || lowMime.contains("tar") || lowMime.contains("rar") || lowMime.contains("compressed") || name.endsWith(".7z")) {
                        iconRes = android.R.drawable.ic_menu_save; // Archive
                    } else if (lowMime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg")) {
                        iconRes = android.R.drawable.ic_media_play; // Audio
                    }
                    
                    com.bumptech.glide.Glide.with(fh.itemView.getContext()).clear(fh.ivThumbnail);
                    fh.ivThumbnail.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                    fh.ivThumbnail.setBackgroundColor(android.graphics.Color.DKGRAY);
                    fh.ivThumbnail.setImageResource(iconRes);
                } else {
                    fh.ivThumbnail.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    fh.ivThumbnail.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    if (thumb != null && thumb.startsWith("data:image/")) {
                        try {
                            String base64Image = thumb.split(",")[1];
                            byte[] imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                            com.bumptech.glide.Glide.with(fh.itemView.getContext())
                                    .load(imageBytes)
                                    .placeholder(android.R.color.darker_gray)
                                    .centerCrop()
                                    .into(fh.ivThumbnail);
                            thumbLoaded = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    
                    if (!thumbLoaded) {
                        String thumbSigned = item.optString("thumbnail_signed_url", "");
                        String loadUrl = thumbSigned.isEmpty() ? publicUrl : thumbSigned;

                        if (loadUrl != null && !loadUrl.isEmpty()) {
                            if (mime.startsWith("video/") && thumbSigned.isEmpty()) {
                                // Skip HTTP video frame decoding as it hangs the Glide queue
                                com.bumptech.glide.Glide.with(fh.itemView.getContext()).clear(fh.ivThumbnail);
                                fh.ivThumbnail.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                                fh.ivThumbnail.setBackgroundColor(android.graphics.Color.DKGRAY);
                                fh.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                            } else {
                                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> fallbackRequest = null;
                                if (!thumbSigned.isEmpty() && publicUrl != null && !publicUrl.isEmpty() && !thumbSigned.equals(publicUrl)) {
                                    fallbackRequest = com.bumptech.glide.Glide.with(fh.itemView.getContext())
                                            .load(publicUrl)
                                            .override(400, 400)
                                            .centerCrop()
                                            .error(android.R.drawable.ic_menu_gallery);
                                }
    
                                // If we already know this thumbnail URL is broken, skip straight to fallback
                                if (brokenThumbUrls.contains(loadUrl) && fallbackRequest != null) {
                                    fallbackRequest.into(fh.ivThumbnail);
                                } else {
                                    com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request = com.bumptech.glide.Glide.with(fh.itemView.getContext())
                                            .load(loadUrl)
                                            .override(400, 400)
                                            .placeholder(android.R.color.darker_gray)
                                            .centerCrop()
                                            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                            @Override
                                            public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                                        boolean isFirstResource) {
                                                brokenThumbUrls.add(loadUrl);
                                                return false;
                                            }
                                            @Override
                                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                                                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                                            com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                                return false;
                                            }
                                        });
                                
                                if (fallbackRequest != null) {
                                    request = request.error(fallbackRequest);
                                } else {
                                    request = request.error(android.R.drawable.ic_menu_gallery);
                                }
                                
                                request.into(fh.ivThumbnail);
                                }
                            }
                        } else {
                            android.util.Log.w("MediaVault", "publicUrl is null/empty for r2Key: " + r2Key + " — r2PublicUrl pref may not be set");
                            fh.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }
                }
                            
                if (publicUrl != null) {
                    fh.itemView.setOnClickListener(v -> {
                        if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            String mimeToUse = mime.isEmpty() ? "*/*" : mime;
                            intent.setDataAndType(android.net.Uri.parse(publicUrl), mimeToUse);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try {
                                startActivity(Intent.createChooser(intent, "Open with"));
                            } catch (Exception e) {
                                android.widget.Toast.makeText(MediaVaultGridActivity.this, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        
                        java.util.List<String> urls = new java.util.ArrayList<>();
                        java.util.List<String> ids = new java.util.ArrayList<>();
                        java.util.List<String> r2Keys = new java.util.ArrayList<>();
                        java.util.List<Boolean> likes = new java.util.ArrayList<>();
                        int selectedIndex = 0;
                        for (int i = 0; i < mediaList.size(); i++) {
                            org.json.JSONObject obj = mediaList.get(i);
                            if ("file".equals(obj.optString("item_type")) && (obj.optString("mime_type", "").startsWith("image/") || obj.optString("mime_type", "").startsWith("video/"))) {
                                String r2KeyInner = obj.optString("r2_key", "");
                                    // Prefer signed URL, fall back to public URL
                                    String innerSigned = obj.optString("signed_url", "");
                                    String u = innerSigned.isEmpty() ? client.getR2PublicUrl(r2KeyInner) : innerSigned;
                                    if (u != null) {
                                        urls.add(u);
                                        ids.add(obj.optString("id"));
                                        r2Keys.add(r2KeyInner);
                                        
                                        boolean isLiked = false;
                                        try {
                                            if (obj.has("vault_liked_files")) {
                                                Object vlf = obj.get("vault_liked_files");
                                                if (vlf instanceof org.json.JSONArray && ((org.json.JSONArray) vlf).length() > 0) {
                                                    isLiked = true;
                                                }
                                            }
                                        } catch (Exception e) {}
                                        likes.add(isLiked);
                                        
                                        if (u.equals(publicUrl)) {
                                            selectedIndex = urls.size() - 1;
                                        }
                                    }
                                }
                            }
                            MediaViewerActivity.currentViewerUrls = urls;
                            MediaViewerActivity.currentViewerFileIds = ids;
                            MediaViewerActivity.currentViewerR2Keys = r2Keys;
                            MediaViewerActivity.currentViewerLikes = likes;
                            MediaViewerActivity.currentViewerIndex = selectedIndex;
                            MediaViewerActivity.currentCollectionId = collectionId;
                            
                            Intent intent = new Intent(MediaVaultGridActivity.this, MediaViewerActivity.class);
                            startActivity(intent);
                    });
                    
                    fh.ivDownload.setOnClickListener(v -> {
                        String url = item.optString("signed_url");
                        if (url.isEmpty()) url = publicUrl;
                        if (url == null || url.isEmpty()) {
                            android.widget.Toast.makeText(MediaVaultGridActivity.this, "Cannot download: No valid URL", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
                            String title = item.optString("filename", "Vault_Download");
                            request.setTitle(title);
                            request.setDescription("Downloading file...");
                            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, title);
                            
                            android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
                            if (manager != null) {
                                manager.enqueue(request);
                                android.widget.Toast.makeText(MediaVaultGridActivity.this, "Downloading " + title + "...", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            android.widget.Toast.makeText(MediaVaultGridActivity.this, "Download failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }

        @Override
        public int getItemCount() { return mediaList.size() + (hasMore ? 1 : 0); }

        class LoadMoreViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.Button btn;
            LoadMoreViewHolder(android.view.View itemView) {
                super(itemView);
                btn = (android.widget.Button) itemView;
            }
        }

        class FileViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.ImageView ivThumbnail, ivPlayIcon, ivDownload;
            android.widget.TextView tvFileName;
            FileViewHolder(android.view.View itemView) {
                super(itemView);
                ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
                ivPlayIcon = itemView.findViewById(R.id.ivPlayIcon);
                ivDownload = itemView.findViewById(R.id.ivDownload);
                tvFileName = itemView.findViewById(R.id.tvFileName);
            }
        }
        
        class FolderViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView tvName, tvType;
            android.widget.ImageView ivIcon, ivSecretLock;
            FolderViewHolder(android.view.View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvType = itemView.findViewById(R.id.tvType);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                ivSecretLock = itemView.findViewById(R.id.ivSecretLock);
            }
        }
    }

    private void uploadFile(Uri uri) {
        new Thread(() -> {
            try {
                String filename = "upload";
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) mimeType = "application/octet-stream";

                long size = 0;
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (nameIndex != -1) filename = cursor.getString(nameIndex);
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex);
                    cursor.close();
                }
                if (size > 0) totalBytesToUpload.addAndGet(size);
                runOnUiThread(this::updateUploadUI);

                String r2Key = collectionPrefix + System.currentTimeMillis() + "-" + filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                String putUrlStr = client.getR2PresignedPutUrl(r2Key, mimeType);

                java.net.URL putUrl = new java.net.URL(putUrlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) putUrl.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", mimeType);
                conn.setDoOutput(true);

                InputStream is = getContentResolver().openInputStream(uri);
                OutputStream os = conn.getOutputStream();
                byte[] buffer = new byte[16384];
                int bytesRead;
                long totalSize = 0;
                long lastUpdate = System.currentTimeMillis();
                
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    totalSize += bytesRead;
                    totalBytesUploaded.addAndGet(bytesRead);
                    
                    if (System.currentTimeMillis() - lastUpdate > 150) {
                        lastUpdate = System.currentTimeMillis();
                        runOnUiThread(this::updateUploadUI);
                    }
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
                } else {
                    throw new Exception("Upload failed: " + conn.getResponseCode());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                uploadsPending.decrementAndGet();
                uploadsCompleted.incrementAndGet();
                runOnUiThread(this::updateUploadUI);
            }
        }).start();
    }
}
