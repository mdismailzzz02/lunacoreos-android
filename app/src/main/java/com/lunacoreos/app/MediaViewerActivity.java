package com.lunacoreos.app;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;

public class MediaViewerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        String imageUrl = getIntent().getStringExtra("IMAGE_URL");

        PhotoView photoView = findViewById(R.id.photoView);
        ImageView btnClose = findViewById(R.id.btnClose);

        btnClose.setOnClickListener(v -> finish());

        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl)
                .into(photoView);
        }
    }
}
