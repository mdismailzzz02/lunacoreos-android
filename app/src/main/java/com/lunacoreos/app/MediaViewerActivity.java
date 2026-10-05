package com.lunacoreos.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.List;

public class MediaViewerActivity extends AppCompatActivity {
    
    public static List<String> currentViewerUrls = null;
    public static List<String> currentViewerFileIds = null;
    public static List<String> deletedFileIds = new java.util.ArrayList<>();
    public static List<String> currentViewerR2Keys = null;
    public static List<Boolean> currentViewerLikes = null;
    public static int currentViewerIndex = 0;
    public static String currentCollectionId = null;
    
    private SupabaseClient client;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);
        
        client = new SupabaseClient(this);

        ImageView btnClose = findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> finish());
        
        ImageView ivLike = findViewById(R.id.ivLike);

        if (currentViewerUrls == null || currentViewerUrls.isEmpty()) {
            finish();
            return;
        }

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new PhotoPagerAdapter(currentViewerUrls));
        viewPager.setCurrentItem(currentViewerIndex, false);
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                boolean isLiked = currentViewerLikes != null && currentViewerLikes.get(position);
                ivLike.setColorFilter(isLiked ? 0xFFEC4899 : 0xFFFFFFFF);
            }
        });
        
        ivLike.setOnClickListener(v -> {
            int pos = viewPager.getCurrentItem();
            if (currentViewerFileIds == null || currentViewerLikes == null) return;
            
            String fileId = currentViewerFileIds.get(pos);
            boolean currentlyLiked = currentViewerLikes.get(pos);
            boolean newLiked = !currentlyLiked;
            
            currentViewerLikes.set(pos, newLiked);
            ivLike.setColorFilter(newLiked ? 0xFFEC4899 : 0xFFFFFFFF);
            
            new Thread(() -> {
                try {
                    client.toggleFileLike(fileId, newLiked);
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> android.widget.Toast.makeText(MediaViewerActivity.this, "Failed to update like", android.widget.Toast.LENGTH_SHORT).show());
                    // Revert UI on fail
                    currentViewerLikes.set(pos, currentlyLiked);
                    runOnUiThread(() -> ivLike.setColorFilter(currentlyLiked ? 0xFFEC4899 : 0xFFFFFFFF));
                }
            }).start();
        });
        
        ImageView ivDelete = findViewById(R.id.ivDelete);
        ivDelete.setOnClickListener(v -> {
            int pos = viewPager.getCurrentItem();
            if (currentViewerFileIds == null || currentViewerFileIds.isEmpty()) return;

            new Thread(() -> {
                try {
                    org.json.JSONObject trashFolder = client.getTrashCollection();
                    boolean inTrash = false;
                    String trashId = null;
                    if (trashFolder != null) {
                        trashId = trashFolder.optString("id");
                        if (trashId.equals(currentCollectionId)) inTrash = true;
                    }
                    
                    final boolean isInTrash = inTrash;
                    final String finalTrashId = trashId;
                    final org.json.JSONObject finalTrashFolder = trashFolder;

                    runOnUiThread(() -> {
                        new android.app.AlertDialog.Builder(this)
                            .setTitle(isInTrash ? "Delete Permanently" : "Move to Trash")
                            .setMessage(isInTrash ? "Are you sure you want to permanently delete this photo? This cannot be undone." : "Are you sure you want to move this photo to the Trash?")
                            .setPositiveButton(isInTrash ? "Delete" : "Move", (dialog, which) -> {
                                String fileId = currentViewerFileIds.get(pos);
                                String r2Key = currentViewerR2Keys != null && currentViewerR2Keys.size() > pos ? currentViewerR2Keys.get(pos) : null;
                                
                                new Thread(() -> {
                                    try {
                                        if (isInTrash || finalTrashFolder == null || r2Key == null) {
                                            client.deleteVaultFile(fileId);
                                            if (r2Key != null) {
                                                try { client.deleteR2File(r2Key); } catch (Exception e2) { e2.printStackTrace(); }
                                            }
                                        } else {
                                            String trashPrefix = finalTrashFolder.optString("key_prefix");
                                            String filename = r2Key.substring(r2Key.lastIndexOf('/') + 1);
                                            String newR2Key = trashPrefix;
                                            if (!newR2Key.endsWith("/")) newR2Key += "/";
                                            newR2Key += filename;
                                            
                                            client.moveR2File(r2Key, newR2Key);
                                            client.moveVaultFile(fileId, finalTrashId, newR2Key);
                                        }
                                        
                                        runOnUiThread(() -> {
                                            deletedFileIds.add(fileId);
                                            currentViewerUrls.remove(pos);
                                            currentViewerFileIds.remove(pos);
                                            if (currentViewerR2Keys != null) currentViewerR2Keys.remove(pos);
                                            if (currentViewerLikes != null) currentViewerLikes.remove(pos);
                                            
                                            if (currentViewerUrls.isEmpty()) {
                                                finish();
                                            } else {
                                                viewPager.getAdapter().notifyDataSetChanged();
                                            }
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                                        runOnUiThread(() -> android.widget.Toast.makeText(MediaViewerActivity.this, "Failed: " + msg, android.widget.Toast.LENGTH_LONG).show());
                                    }
                                }).start();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> android.widget.Toast.makeText(MediaViewerActivity.this, "Error checking trash folder", android.widget.Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
        
        SwipeToDismissLayout swipeLayout = findViewById(R.id.swipeLayout);
        View rootFrame = findViewById(android.R.id.content);
        swipeLayout.setBackgroundView(rootFrame);
    }

    private class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder> {
        private final List<String> urls;

        public PhotoPagerAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_viewer_photo, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            String url = urls.get(position);
            String r2Key = currentViewerR2Keys != null && currentViewerR2Keys.size() > position ? currentViewerR2Keys.get(position) : "";
            boolean isPdf = r2Key.toLowerCase().endsWith(".pdf") || url.toLowerCase().contains(".pdf");
            
            if (isPdf) {
                holder.photoView.setVisibility(View.GONE);
                holder.pdfWebView.setVisibility(View.VISIBLE);
                holder.pdfWebView.getSettings().setJavaScriptEnabled(true);
                holder.pdfWebView.getSettings().setSupportZoom(true);
                holder.pdfWebView.getSettings().setBuiltInZoomControls(true);
                holder.pdfWebView.getSettings().setDisplayZoomControls(false);
                holder.pdfWebView.setWebViewClient(new WebViewClient());
                try {
                    holder.pdfWebView.loadUrl("https://docs.google.com/viewer?url=" + java.net.URLEncoder.encode(url, "UTF-8") + "&embedded=true");
                } catch (Exception e) {}
            } else {
                holder.pdfWebView.setVisibility(View.GONE);
                holder.photoView.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView.getContext())
                    .load(url)
                    .into(holder.photoView);
            }
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            PhotoView photoView;
            WebView pdfWebView;
            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                photoView = itemView.findViewById(R.id.photoView);
                pdfWebView = itemView.findViewById(R.id.pdfWebView);
            }
        }
    }
}
