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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private TagAdapter tagAdapter;
    private List<TagItem> tagList = new ArrayList<>();
    private EditText etSearch;

    class TagItem {
        String name;
        int count;
        TagItem(String name, int count) { this.name = name; this.count = count; }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_linkbox, container, false);

        supabaseClient = new SupabaseClient(requireContext());
        
        tvTitle = view.findViewById(R.id.tvTitle);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (etSearch != null && etSearch.hasFocus()) {
                etSearch.clearFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
            fetchLinks();
        });

        adapter = new LinkAdapter();
        recyclerView.setAdapter(adapter);

        etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    view.findViewById(R.id.rvTags).setVisibility(View.GONE);
                } else if (etSearch.hasFocus()) {
                    view.findViewById(R.id.rvTags).setVisibility(View.VISIBLE);
                }
                filterLinks(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etSearch.getText().length() == 0) {
                view.findViewById(R.id.rvTags).setVisibility(View.VISIBLE);
            } else if (!hasFocus) {
                view.findViewById(R.id.rvTags).setVisibility(View.GONE);
            }
        });
        
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && etSearch.hasFocus()) {
                    etSearch.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                }
            }
        });
        
        recyclerView.setOnTouchListener((v, event) -> {
            if (etSearch.hasFocus()) {
                etSearch.clearFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
            return false; // allow recyclerView to handle the event
        });

        RecyclerView rvTags = view.findViewById(R.id.rvTags);
        rvTags.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), 2));
        tagAdapter = new TagAdapter();
        rvTags.setAdapter(tagAdapter);

        view.findViewById(R.id.fabAddLink).setOnClickListener(v -> showAddLinkDialog());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new androidx.activity.OnBackPressedCallback(true) {
            long lastBackPressTime = 0;
            @Override
            public void handleOnBackPressed() {
                if (etSearch != null && etSearch.hasFocus()) {
                    etSearch.clearFocus();
                } else if (etSearch != null && etSearch.getText().length() > 0) {
                    etSearch.setText("");
                    etSearch.clearFocus();
                } else {
                    if (System.currentTimeMillis() - lastBackPressTime < 2000) {
                        setEnabled(false);
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    } else {
                        lastBackPressTime = System.currentTimeMillis();
                        Toast.makeText(getContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        
        // Also clear focus if the keyboard is dismissed via back gesture
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            boolean isKeyboardVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
            if (!isKeyboardVisible && etSearch != null && etSearch.hasFocus()) {
                etSearch.clearFocus();
            }
            return androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets);
        });

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
        
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        if (tvDialogTitle != null) {
            tvDialogTitle.setText(existingItem == null ? "Save Link" : "Edit Link");
        }

        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
        bottomSheetDialog.setContentView(dialogView);
        // Ensure background is transparent so custom shape shows
        ((View) dialogView.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);
        
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(getContext(), "URL cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            saveLink(url, etTitle.getText().toString().trim(), etDescription.getText().toString().trim(), etTags.getText().toString().trim(), existingItem == null ? null : existingItem.optString("id"));
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
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
                java.util.Map<String, Integer> tagCounts = new java.util.HashMap<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    linkList.add(obj);
                    
                    String tags = obj.optString("tags", "");
                    if (!tags.isEmpty()) {
                        for (String tag : tags.split(",")) {
                            String t = tag.trim();
                            if (!t.isEmpty()) {
                                tagCounts.put(t, tagCounts.getOrDefault(t, 0) + 1);
                            }
                        }
                    }
                }
                
                tagList.clear();
                for (java.util.Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
                    tagList.add(new TagItem(entry.getKey(), entry.getValue()));
                }
                tagList.sort((a, b) -> b.count - a.count); // sort by count descending

                requireActivity().runOnUiThread(() -> {
                    tagAdapter.notifyDataSetChanged();
                    filterLinks(etSearch != null ? etSearch.getText().toString() : "");
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
            String title = link.optString("title", "").toLowerCase();
            String tags = link.optString("tags", "").toLowerCase();
            if (url.contains(lowerQuery) || desc.contains(lowerQuery) || title.contains(lowerQuery) || tags.contains(lowerQuery)) {
                filteredList.add(link);
            }
        }
        
        if (tvTitle != null) {
            tvTitle.setText("Linkbox (" + filteredList.size() + ")");
        }
        
        adapter.notifyDataSetChanged();
    }

    private class TagAdapter extends RecyclerView.Adapter<TagAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tag, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TagItem item = tagList.get(position);
            holder.tvTagName.setText(item.name);
            holder.tvTagCount.setText(String.valueOf(item.count));
            
            holder.itemView.setOnClickListener(v -> {
                etSearch.setText(item.name);
                etSearch.clearFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            });
        }

        @Override
        public int getItemCount() {
            return tagList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTagName, tvTagCount;
            ViewHolder(View itemView) {
                super(itemView);
                tvTagName = itemView.findViewById(R.id.tvTagName);
                tvTagCount = itemView.findViewById(R.id.tvTagCount);
            }
        }
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
            
            if (date.length() > 19) date = date.substring(0, 19);
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date parsedDate = sdf.parse(date);
                java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());
                out.setTimeZone(java.util.TimeZone.getDefault());
                holder.tvDate.setText(out.format(parsedDate));
            } catch (Exception e) {
                if (date.length() > 10) date = date.substring(0, 10);
                holder.tvDate.setText(date);
            }

            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Invalid URL", Toast.LENGTH_SHORT).show();
                }
            });
            
            holder.ivDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Delete Link")
                    .setMessage("Are you sure you want to delete this link?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteLink(item.optString("id")))
                    .setNegativeButton("Cancel", null)
                    .show();
            });
            
            holder.ivEdit.setOnClickListener(v -> {
                showEditLinkDialog(item);
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
            android.widget.ImageView ivDelete, ivEdit;
            
            ViewHolder(View itemView) {
                super(itemView);
                tvUrl = itemView.findViewById(R.id.tvUrl);
                tvDesc = itemView.findViewById(R.id.tvDesc);
                tvDate = itemView.findViewById(R.id.tvDate);
                ivDelete = itemView.findViewById(R.id.ivDelete);
                ivEdit = itemView.findViewById(R.id.ivEdit);
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
