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

import java.util.List;

public class MediaViewerActivity extends AppCompatActivity {
    
    public static List<String> currentViewerUrls = null;
    public static int currentViewerIndex = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        ImageView btnClose = findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> finish());

        if (currentViewerUrls == null || currentViewerUrls.isEmpty()) {
            finish();
            return;
        }

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new PhotoPagerAdapter(currentViewerUrls));
        viewPager.setCurrentItem(currentViewerIndex, false);
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
            Glide.with(holder.itemView.getContext())
                .load(url)
                .into(holder.photoView);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            PhotoView photoView;
            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                photoView = itemView.findViewById(R.id.photoView);
            }
        }
    }
}
