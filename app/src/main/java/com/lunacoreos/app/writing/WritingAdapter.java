package com.lunacoreos.app.writing;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.lunacoreos.app.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WritingAdapter extends RecyclerView.Adapter<WritingAdapter.ViewHolder> {

    private List<JSONObject> drafts = new ArrayList<>();
    private final OnDraftClickListener listener;
    private final SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy", Locale.US);
    
    {
        apiFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        displayFormat.setTimeZone(java.util.TimeZone.getDefault());
    }

    public interface OnDraftClickListener {
        void onDraftClick(JSONObject draft);
    }

    public WritingAdapter(OnDraftClickListener listener) {
        this.listener = listener;
    }

    public void setDrafts(List<JSONObject> drafts) {
        this.drafts = drafts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_writing_draft, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject draft = drafts.get(position);
        try {
            holder.tvTitle.setText(draft.optString("title", "Untitled"));
            
            // Clean HTML tags from content for preview
            String htmlContent = draft.optString("content", "");
            String plainText = htmlContent.replaceAll("<[^>]*>", "").trim();
            holder.tvPreview.setText(plainText.isEmpty() ? "No content yet..." : plainText);

            // Format date
            String dateStr = draft.optString("updatedAt", draft.optString("created_at", ""));
            try {
                if (!dateStr.isEmpty()) {
                    Date date = apiFormat.parse(dateStr.substring(0, 19));
                    holder.tvDate.setText(displayFormat.format(date));
                } else {
                    holder.tvDate.setText("");
                }
            } catch (ParseException e) {
                holder.tvDate.setText(dateStr);
            }

            // Tags
            String tags = draft.optString("tags", "");
            if (!tags.isEmpty()) {
                holder.tvTag.setVisibility(View.VISIBLE);
                holder.tvTag.setText(tags.split(",")[0].trim());
            } else {
                holder.tvTag.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> listener.onDraftClick(draft));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return drafts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvPreview, tvDate, tvTag;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvPreview = itemView.findViewById(R.id.tvPreview);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTag = itemView.findViewById(R.id.tvTag);
        }
    }
}
