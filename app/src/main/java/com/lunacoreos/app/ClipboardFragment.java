package com.lunacoreos.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ClipboardFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvClips;
    private ClipboardAdapter adapter;
    private List<JSONObject> clipList = new ArrayList<>();
    private List<JSONObject> filteredList = new ArrayList<>();
    
    private String vaultMode = "normal"; // normal, hidden, secret
    private boolean isVaultUnlocked = true;
    private long lastTitleClickTime = 0;
    private int titleClickCount = 0;
    
    private EditText etSearch;

    private ActivityResultLauncher<Intent> appPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                    isVaultUnlocked = true;
                    vaultMode = "secret";
                    TextView tvTitle = getView().findViewById(R.id.tvTitle);
                    tvTitle.setText("Secret Clipboard");
                    tvTitle.setTextColor(0xFFEF4444);
                    loadClips();
                } else {
                    vaultMode = "normal";
                    isVaultUnlocked = false;
                    TextView tvTitle = getView().findViewById(R.id.tvTitle);
                    tvTitle.setText("Clipboard");
                    tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
                    loadClips();
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clipboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        rvClips = view.findViewById(R.id.rvClips);
        rvClips.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ClipboardAdapter();
        rvClips.setAdapter(adapter);

        etSearch = view.findViewById(R.id.etSearch);
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

        etSearch.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                if (etSearch.getCompoundDrawables()[2] != null) {
                    if (event.getRawX() >= (etSearch.getRight() - etSearch.getCompoundDrawables()[2].getBounds().width() - etSearch.getPaddingRight() - 32)) {
                        etSearch.setText("");
                        return true;
                    }
                }
            }
            return false;
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    etSearch.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_close_clear_cancel, 0);
                } else {
                    etSearch.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                }
                filterClips(s.toString());
            }
        });

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        tvTitle.setOnClickListener(v -> {
            if (etSearch.getVisibility() == android.view.View.VISIBLE) {
                unfocusSearch.onClick(v);
                return;
            }
            if (!"normal".equals(vaultMode)) {
                vaultMode = "normal";
                isVaultUnlocked = false;
                tvTitle.setText("Clipboard");
                tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
                loadClips();
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastTitleClickTime > 500) titleClickCount = 0;
            lastTitleClickTime = now;
            titleClickCount++;
            if (titleClickCount == 3) {
                titleClickCount = 0;
                Intent intent = new Intent(getContext(), AppPasswordActivity.class);
                intent.putExtra("LOCK_ID", "clipboard_secret");
                intent.putExtra("LOCK_TITLE", "Secret Clipboard");
                intent.putExtra("VAULT_MODE", "secret");
                appPasswordLauncher.launch(intent);
            }
        });

        tvTitle.setOnLongClickListener(v -> {
            if (!"normal".equals(vaultMode)) {
                vaultMode = "normal";
                isVaultUnlocked = false;
                tvTitle.setText("Clipboard");
                tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
                loadClips();
            } else {
                Intent intent = new Intent(getContext(), AppPasswordActivity.class);
                intent.putExtra("LOCK_ID", "clipboard_secret");
                intent.putExtra("LOCK_TITLE", "Secret Clipboard");
                intent.putExtra("VAULT_MODE", "secret");
                appPasswordLauncher.launch(intent);
            }
            return true;
        });

        swipeRefresh.setOnRefreshListener(() -> {
            if (isVaultUnlocked) {
                loadClips();
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });

        view.findViewById(R.id.fabPaste).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
                if (text != null && !text.toString().trim().isEmpty()) {
                    Intent intent = new Intent(getContext(), SaveClipboardActivity.class);
                    intent.setAction(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, text.toString());
                    intent.putExtra("IS_SECRET_MODE", "secret".equals(vaultMode));
                    startActivity(intent);
                } else {
                    Toast.makeText(getContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });

        loadClips();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadClips();
    }

    private void filterClips(String query) {
        filteredList.clear();
        String q = query.toLowerCase();
        for (JSONObject clip : clipList) {
            String content = clip.optString("content", "").toLowerCase();
            if (content.contains(q)) {
                filteredList.add(clip);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void saveClip(String content, boolean isSecret) {
        swipeRefresh.setRefreshing(true);
        new Thread(() -> {
            try {
                SupabaseClient client = new SupabaseClient(getContext());
                client.saveClipboardText(content, isSecret);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Saved to " + (isSecret ? "Secret" : "Public") + " Clipboard", Toast.LENGTH_SHORT).show();
                    loadClips();
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    swipeRefresh.setRefreshing(false);
                });
            }
        }).start();
    }

    private void loadClips() {
        swipeRefresh.setRefreshing(true);
        new Thread(() -> {
            try {
                SupabaseClient client = new SupabaseClient(getContext());
                String mode = vaultMode;
                JSONArray arr = client.getClipboardClips("secret".equals(mode));

                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getJSONObject(i));
                }

                requireActivity().runOnUiThread(() -> {
                    clipList = list;
                    TextView tvTitle = getView().findViewById(R.id.tvTitle);
                    if ("secret".equals(vaultMode)) {
                        tvTitle.setText("Secret Clipboard");
                        tvTitle.setTextColor(0xFFEC4899);
                    } else {
                        tvTitle.setText("Clipboard");
                        tvTitle.setTextColor(getResources().getColor(R.color.text_primary));
                    }
                    filterClips(etSearch.getText().toString());
                    swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Failed to load clips: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    swipeRefresh.setRefreshing(false);
                });
            }
        }).start();
    }

    private class ClipboardAdapter extends RecyclerView.Adapter<ClipboardAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_clipboard, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject clip = filteredList.get(position);
            holder.tvContent.setText(clip.optString("content"));
            
            String dateStr = clip.optString("created_at");
            if (dateStr.length() > 19) dateStr = dateStr.substring(0, 19);
            dateStr = dateStr.replace(" ", "T");
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(dateStr);
                SimpleDateFormat out = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                out.setTimeZone(java.util.TimeZone.getDefault());
                holder.tvDate.setText(out.format(date));
            } catch (Exception e) {
                holder.tvDate.setText(dateStr);
            }

            holder.ivSecretLock.setVisibility(clip.optBoolean("is_secret") ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("Lunacore Clip", clip.optString("content"));
                clipboard.setPrimaryClip(clipData);
                Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
            });

            holder.ivDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                    .setTitle("Delete Clip")
                    .setMessage("Are you sure you want to delete this clip?")
                    .setPositiveButton("Delete", (d, w) -> {
                        String id = clip.optString("id");
                        new Thread(() -> {
                            try {
                                SupabaseClient client = new SupabaseClient(getContext());
                                client.deleteClipboardClip(id);
                                requireActivity().runOnUiThread(() -> loadClips());
                            } catch (Exception e) {
                                e.printStackTrace();
                                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Failed to delete", Toast.LENGTH_SHORT).show());
                            }
                        }).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvContent, tvDate;
            ImageView ivSecretLock, ivDelete;
            ViewHolder(View itemView) {
                super(itemView);
                tvContent = itemView.findViewById(R.id.tvContent);
                tvDate = itemView.findViewById(R.id.tvDate);
                ivSecretLock = itemView.findViewById(R.id.ivSecretLock);
                ivDelete = itemView.findViewById(R.id.ivDelete);
            }
        }
    }
}
