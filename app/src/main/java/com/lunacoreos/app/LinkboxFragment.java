package com.lunacoreos.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LinkboxFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinkAdapter adapter;
    private List<JSONObject> linkList = new ArrayList<>();
    private List<JSONObject> filteredList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private SupabaseClient supabaseClient;
    private TextView tvTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_linkbox, container, false);

        supabaseClient = new SupabaseClient(requireContext());
        
        tvTitle = view.findViewById(R.id.tvTitle);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::fetchLinks);

        adapter = new LinkAdapter();
        recyclerView.setAdapter(adapter);

        EditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLinks(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.fabAddLink).setOnClickListener(v -> showAddLinkDialog());

        fetchLinks();
        return view;
    }

    private void showAddLinkDialog() {
        showEditLinkDialog(null);
    }

    private void showEditLinkDialog(JSONObject existingItem) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_link, null);
        EditText etUrl = dialogView.findViewById(R.id.etUrl);
        EditText etTitle = dialogView.findViewById(R.id.etTitle);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etTags = dialogView.findViewById(R.id.etTags);

        if (existingItem != null) {
            etUrl.setText(existingItem.optString("url", ""));
            etTitle.setText(existingItem.optString("title", ""));
            etDescription.setText(existingItem.optString("description", ""));
            etTags.setText(existingItem.optString("tags", ""));
        }

        new AlertDialog.Builder(getContext())
                .setTitle(existingItem == null ? "Add Link" : "Edit Link")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String url = etUrl.getText().toString().trim();
                    if (url.isEmpty()) {
                        Toast.makeText(getContext(), "URL cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveLink(url, etTitle.getText().toString().trim(), etDescription.getText().toString().trim(), etTags.getText().toString().trim(), existingItem == null ? null : existingItem.optString("id"));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveLink(String url, String title, String description, String tags, String existingId) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("id", existingId == null ? java.util.UUID.randomUUID().toString() : existingId);
                payload.put("url", url);
                if (!title.isEmpty()) payload.put("title", title);
                if (!description.isEmpty()) payload.put("description", description);
                if (!tags.isEmpty()) payload.put("tags", tags);

                supabaseClient.saveLinkboxEntry(payload);
                
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Link saved", Toast.LENGTH_SHORT).show();
                    fetchLinks();
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void fetchLinks() {
        swipeRefreshLayout.setRefreshing(true);
        new Thread(() -> {
            try {
                JSONArray arr = supabaseClient.getLinkboxEntries();
                linkList.clear();
                for (int i = 0; i < arr.length(); i++) {
                    linkList.add(arr.getJSONObject(i));
                }
                requireActivity().runOnUiThread(() -> {
                    filterLinks("");
                    swipeRefreshLayout.setRefreshing(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Failed to fetch links: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        }).start();
    }

    private void filterLinks(String query) {
        filteredList.clear();
        String lowerQuery = query.toLowerCase();
        for (JSONObject link : linkList) {
            String url = link.optString("url", "").toLowerCase();
            String desc = link.optString("description", "").toLowerCase();
            if (url.contains(lowerQuery) || desc.contains(lowerQuery)) {
                filteredList.add(link);
            }
        }
        
        if (tvTitle != null) {
            tvTitle.setText("Linkbox (" + filteredList.size() + ")");
        }
        
        adapter.notifyDataSetChanged();
    }

    private class LinkAdapter extends RecyclerView.Adapter<LinkAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_link, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject item = filteredList.get(position);
            String url = item.optString("url", "No URL");
            String title = item.optString("title", "");
            String desc = item.optString("description", "");
            String tags = item.optString("tags", "");
            String date = item.optString("created_at", "");
            
            if (!title.isEmpty()) {
                holder.tvUrl.setText(title);
            } else {
                holder.tvUrl.setText(url);
            }

            StringBuilder descText = new StringBuilder();
            if (!desc.isEmpty()) descText.append(desc);
            if (!tags.isEmpty()) {
                if (descText.length() > 0) descText.append("\n");
                descText.append("Tags: ").append(tags);
            }
            holder.tvDesc.setText(descText.toString().isEmpty() ? url : descText.toString());
            
            if (date.length() > 10) date = date.substring(0, 10);
            holder.tvDate.setText(date);

            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Invalid URL", Toast.LENGTH_SHORT).show();
                }
            });
            
            holder.ivDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                    .setTitle("Delete Link")
                    .setMessage("Are you sure you want to delete this link?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteLink(item.optString("id")))
                    .setNegativeButton("Cancel", null)
                    .show();
            });
            
            holder.itemView.setOnLongClickListener(v -> {
                showEditLinkDialog(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvUrl, tvDesc, tvDate;
            android.widget.ImageView ivDelete;
            
            ViewHolder(View itemView) {
                super(itemView);
                tvUrl = itemView.findViewById(R.id.tvUrl);
                tvDesc = itemView.findViewById(R.id.tvDesc);
                tvDate = itemView.findViewById(R.id.tvDate);
                ivDelete = itemView.findViewById(R.id.ivDelete);
            }
        }
    }
    
    private void deleteLink(String id) {
        new Thread(() -> {
            try {
                supabaseClient.deleteLinkboxEntry(id);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                    fetchLinks();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
