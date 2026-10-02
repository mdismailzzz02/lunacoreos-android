package com.lunacoreos.app.writing;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.lunacoreos.app.R;
import com.lunacoreos.app.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WritingListFragment extends Fragment {

    private RecyclerView recyclerView;
    private WritingAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private EditText etSearch;
    private TextView tvTitle;

    private SupabaseClient supabase;
    private List<JSONObject> allDrafts = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int secretClickCount = 0;
    private String currentMode = "normal"; // "normal" or "secret"

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_writing_list, container, false);

        SharedPreferences prefs = requireContext().getSharedPreferences("LunaCorePrefs", Context.MODE_PRIVATE);
        String url = prefs.getString("supabaseUrl", "");
        String key = prefs.getString("supabaseKey", "");
        String token = prefs.getString("authToken", "");
        
        supabase = new SupabaseClient(url, key);
        supabase.setAuthToken(token);

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        etSearch = view.findViewById(R.id.etSearch);
        tvTitle = view.findViewById(R.id.tvTitle);

        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        adapter = new WritingAdapter(draft -> openEditor(draft.optString("id"), draft.optString("title"), draft.optString("content"), draft.optString("tags"), draft.optString("mode", currentMode)));
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.fabNew).setOnClickListener(v -> openEditor("new", "", "", "", currentMode));

        swipeRefresh.setOnRefreshListener(this::loadDrafts);

        android.widget.ImageView ivSearch = view.findViewById(R.id.ivSearch);
        android.view.View llHeader = view.findViewById(R.id.llHeader);

        android.view.View.OnClickListener unfocusSearch = v -> {
            if (etSearch.getVisibility() == android.view.View.VISIBLE) {
                etSearch.clearFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                if (etSearch.getText().length() == 0) {
                    etSearch.setVisibility(android.view.View.GONE);
                }
            }
        };

        ivSearch.setOnClickListener(v -> {
            if (etSearch.getVisibility() == android.view.View.VISIBLE) {
                etSearch.setText("");
                etSearch.setVisibility(android.view.View.GONE);
                etSearch.clearFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            } else {
                etSearch.setVisibility(android.view.View.VISIBLE);
                etSearch.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        llHeader.setOnClickListener(unfocusSearch);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filterDrafts(s.toString());
            }
        });

        tvTitle.setOnClickListener(v -> {
            if (etSearch.getVisibility() == android.view.View.VISIBLE) {
                unfocusSearch.onClick(v);
                return;
            }
            if (currentMode.equals("secret")) return;
            secretClickCount++;
            if (secretClickCount >= 3) {
                secretClickCount = 0;
                // In a full implementation, we'd prompt for the SecondaryVaultLock PIN here.
                // For now, we just switch modes.
                currentMode = "secret";
                tvTitle.setText("Secret Writings");
                tvTitle.setTextColor(getResources().getColor(R.color.danger, null));
                loadDrafts();
            }
            mainHandler.postDelayed(() -> secretClickCount = 0, 3000);
        });

        loadDrafts();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDrafts(); // reload in case we just saved one
    }

    private void loadDrafts() {
        swipeRefresh.setRefreshing(true);
        executor.execute(() -> {
            try {
                JSONArray draftsArray = supabase.getWritings(currentMode);
                List<JSONObject> loaded = new ArrayList<>();
                for (int i = 0; i < draftsArray.length(); i++) {
                    loaded.add(draftsArray.getJSONObject(i));
                }
                mainHandler.post(() -> {
                    allDrafts = loaded;
                    filterDrafts(etSearch.getText().toString());
                    swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "Failed to load drafts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    swipeRefresh.setRefreshing(false);
                });
            }
        });
    }

    private void filterDrafts(String query) {
        String q = query.toLowerCase().trim();
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject draft : allDrafts) {
            String title = draft.optString("title", "").toLowerCase();
            String tags = draft.optString("tags", "").toLowerCase();
            String content = draft.optString("content", "").toLowerCase();
            if (q.isEmpty() || title.contains(q) || tags.contains(q) || content.contains(q)) {
                filtered.add(draft);
            }
        }
        adapter.setDrafts(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openEditor(String id, String title, String content, String tags, String mode) {
        Intent intent = new Intent(getContext(), WritingEditorActivity.class);
        intent.putExtra("id", id);
        intent.putExtra("title", title);
        intent.putExtra("content", content);
        intent.putExtra("tags", tags);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }
}
