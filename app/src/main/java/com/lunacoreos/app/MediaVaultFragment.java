package com.lunacoreos.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;

public class MediaVaultFragment extends Fragment {

    private RecyclerView rvCollections;
    private SwipeRefreshLayout swipeRefresh;
    private CollectionAdapter adapter;
    private List<JSONObject> collectionList = new ArrayList<>();
    private String vaultMode = "normal";
    private int titleClickCount = 0;
    private long lastTitleClickTime = 0;
    private static boolean isVaultUnlocked = false;

    private final ActivityResultLauncher<Intent> vaultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    loadCollections();
                } else {
                    Toast.makeText(getContext(), "Vault locked.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<Intent> appPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        String mode = data.getStringExtra("VAULT_MODE");
                        if (mode != null) {
                            vaultMode = mode;
                            isVaultUnlocked = true;
                            loadCollections();
                            return;
                        }
                    } else if ("normal".equals(vaultMode)) {
                        isVaultUnlocked = true;
                        loadCollections();
                        return;
                    }
                }
                Toast.makeText(getContext(), "Access Denied", Toast.LENGTH_SHORT).show();
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_media_vault, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        rvCollections = view.findViewById(R.id.rvCollections);
        rvCollections.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CollectionAdapter();
        rvCollections.setAdapter(adapter);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        tvTitle.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastTitleClickTime > 500) titleClickCount = 0;
            lastTitleClickTime = now;
            titleClickCount++;
            if (titleClickCount == 3) {
                titleClickCount = 0;
                if (!"normal".equals(vaultMode)) {
                    vaultMode = "normal";
                    loadCollections();
                } else {
                    Intent intent = new Intent(getContext(), AppPasswordActivity.class);
                    intent.putExtra("LOCK_ID", "vault_hidden");
                    intent.putExtra("LOCK_TITLE", "Hidden Vault");
                    intent.putExtra("VAULT_MODE", "hidden");
                    appPasswordLauncher.launch(intent);
                }
            }
        });

        tvTitle.setOnLongClickListener(v -> {
            if (!"normal".equals(vaultMode)) {
                vaultMode = "normal";
                loadCollections();
            } else {
                Intent intent = new Intent(getContext(), AppPasswordActivity.class);
                intent.putExtra("LOCK_ID", "vault_secret");
                intent.putExtra("LOCK_TITLE", "Secret Vault");
                intent.putExtra("VAULT_MODE", "secret");
                appPasswordLauncher.launch(intent);
            }
            return true;
        });

        swipeRefresh.setOnRefreshListener(() -> {
            if (isVaultUnlocked) {
                loadCollections();
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });

        view.findViewById(R.id.fabAddCollection).setOnClickListener(v -> {
            com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
            View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_vault_collection, null);
            dialog.setContentView(sheetView);

            android.widget.EditText etName = sheetView.findViewById(R.id.etCollectionName);
            android.widget.Spinner spinnerType = sheetView.findViewById(R.id.spinnerType);
            android.widget.CheckBox cbIsSecret = sheetView.findViewById(R.id.cbIsSecret);

            sheetView.findViewById(R.id.btnCreateCollection).setOnClickListener(btn -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) return;
                
                String type = spinnerType.getSelectedItem().toString().toLowerCase();
                boolean isSecret = cbIsSecret.isChecked();
                
                btn.setEnabled(false);
                new Thread(() -> {
                    try {
                        SupabaseClient client = new SupabaseClient(getContext());
                        String userId = client.getUserId();
                        if (userId == null) throw new Exception("Not authenticated");
                        
                        String safeName = name.toLowerCase().replaceAll("[^a-z0-9]", "-");
                        String prefix = "vault/" + userId + "/" + safeName + "/";
                        
                        JSONObject col = new JSONObject();
                        col.put("name", name);
                        col.put("type", type);
                        col.put("is_secret", "secret".equals(vaultMode) || isSecret);
                        col.put("is_hidden", "hidden".equals(vaultMode));
                        col.put("key_prefix", prefix);
                        
                        client.createVaultCollection(col);
                        
                        requireActivity().runOnUiThread(() -> {
                            dialog.dismiss();
                            loadCollections();
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        requireActivity().runOnUiThread(() -> {
                            btn.setEnabled(true);
                            Toast.makeText(getContext(), "Failed to create", Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            });
            dialog.show();
        });

        if (!isVaultUnlocked) {
            Intent lockIntent = new Intent(getContext(), AppPasswordActivity.class);
            lockIntent.putExtra("LOCK_ID", "vault");
            lockIntent.putExtra("LOCK_TITLE", "Unlock Media Vault");
            lockIntent.putExtra("VAULT_MODE", "normal");
            appPasswordLauncher.launch(lockIntent);
        } else {
            loadCollections();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isVaultUnlocked && collectionList.isEmpty()) {
            loadCollections();
        }
    }

    private void loadCollections() {
        swipeRefresh.setRefreshing(true);
        new Thread(() -> {
            try {
                SupabaseClient client = new SupabaseClient(getContext());
                JSONArray arr = client.getVaultCollections(vaultMode);
                
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    boolean hasParent = c.has("parent_id") && !c.isNull("parent_id");
                    String name = c.optString("name", "").toLowerCase();
                    if (!hasParent) {
                        list.add(c);
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    collectionList = list;
                    adapter.notifyDataSetChanged();
                    swipeRefresh.setRefreshing(false);
                    
                    TextView tvTitle = getView().findViewById(R.id.tvTitle);
                    if ("hidden".equals(vaultMode)) {
                        tvTitle.setText("Hidden Vault");
                        tvTitle.setTextColor(0xFFA78BFA);
                    } else if ("secret".equals(vaultMode)) {
                        tvTitle.setText("Secret Vault");
                        tvTitle.setTextColor(0xFFEC4899);
                    } else {
                        tvTitle.setText("Media Vault");
                        tvTitle.setTextColor(getResources().getColor(R.color.text_primary));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Failed to load collections", Toast.LENGTH_LONG).show();
                    swipeRefresh.setRefreshing(false);
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

    private class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_collection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject col = collectionList.get(position);
            holder.tvName.setText(col.optString("name", "Unnamed"));
            String type = col.optString("type", "gallery");
            int fileCount = col.optInt("file_count", 0);
            long sizeBytes = col.optLong("size_bytes", 0);
            String info = type.substring(0, 1).toUpperCase() + type.substring(1);
            if (fileCount > 0 || sizeBytes > 0) {
                info += " · " + fileCount + " file" + (fileCount != 1 ? "s" : "")
                      + " · " + formatBytes(sizeBytes);
            }
            holder.tvType.setText(info);
            
            boolean isSecret = col.optBoolean("is_secret", false);
            holder.ivSecretLock.setVisibility(isSecret ? View.VISIBLE : View.GONE);

            if (type.equals("gallery")) holder.ivIcon.setImageResource(android.R.drawable.ic_menu_gallery);
            else if (type.equals("documents")) holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
            else holder.ivIcon.setImageResource(android.R.drawable.ic_menu_manage);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), MediaVaultGridActivity.class);
                intent.putExtra("COLLECTION_ID", col.optString("id"));
                intent.putExtra("COLLECTION_PREFIX", col.optString("key_prefix"));
                intent.putExtra("COLLECTION_NAME", col.optString("name"));
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Delete Collection")
                    .setMessage("Delete \"" + col.optString("name") + "\" and all its files?")
                    .setPositiveButton("Delete", (d, w) -> {
                        new Thread(() -> {
                            try {
                                SupabaseClient client = new SupabaseClient(getContext());
                                client.deleteVaultCollection(col.optString("id"));
                                requireActivity().runOnUiThread(() -> loadCollections());
                            } catch (Exception e) {
                                e.printStackTrace();
                                requireActivity().runOnUiThread(() ->
                                    Toast.makeText(getContext(), "Failed to delete", Toast.LENGTH_SHORT).show());
                            }
                        }).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return collectionList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvType;
            ImageView ivIcon, ivSecretLock;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvType = itemView.findViewById(R.id.tvType);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                ivSecretLock = itemView.findViewById(R.id.ivSecretLock);
            }
        }
    }
}
