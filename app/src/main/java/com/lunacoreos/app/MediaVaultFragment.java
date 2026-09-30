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

    private final ActivityResultLauncher<Intent> vaultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    loadCollections();
                } else {
                    // Lock failed or cancelled
                    Toast.makeText(getContext(), "Vault locked.", Toast.LENGTH_SHORT).show();
                }
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

        swipeRefresh.setOnRefreshListener(() -> {
            if (CryptoService.hasSessionKey()) {
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
                        JSONObject col = new JSONObject();
                        col.put("name", name);
                        col.put("type", type);
                        col.put("is_secret", isSecret);
                        col.put("key_prefix", "col_" + System.currentTimeMillis() + "/");
                        
                        SupabaseClient client = new SupabaseClient(getContext());
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

        if (!CryptoService.hasSessionKey()) {
            vaultLauncher.launch(new Intent(getContext(), VaultLockActivity.class));
        } else {
            loadCollections();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (CryptoService.hasSessionKey() && collectionList.isEmpty()) {
            loadCollections();
        }
    }

    private void loadCollections() {
        swipeRefresh.setRefreshing(true);
        new Thread(() -> {
            try {
                SupabaseClient client = new SupabaseClient(getContext());
                JSONArray arr = client.getVaultCollections();
                
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getJSONObject(i));
                }

                requireActivity().runOnUiThread(() -> {
                    collectionList = list;
                    adapter.notifyDataSetChanged();
                    swipeRefresh.setRefreshing(false);
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
            String info = type.substring(0, 1).toUpperCase() + type.substring(1)
                    + " · " + fileCount + " file" + (fileCount != 1 ? "s" : "")
                    + " · " + formatBytes(sizeBytes);
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
