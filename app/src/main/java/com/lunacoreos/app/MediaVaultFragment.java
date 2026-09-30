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
            // TODO: Open add collection bottom sheet
            Toast.makeText(getContext(), "Add Collection coming soon", Toast.LENGTH_SHORT).show();
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
            holder.tvType.setText(type.substring(0, 1).toUpperCase() + type.substring(1));
            
            boolean isSecret = col.optBoolean("is_secret", false);
            holder.ivSecretLock.setVisibility(isSecret ? View.VISIBLE : View.GONE);

            if (type.equals("gallery")) holder.ivIcon.setImageResource(android.R.drawable.ic_menu_gallery);
            else if (type.equals("documents")) holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
            else holder.ivIcon.setImageResource(android.R.drawable.ic_menu_manage);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), MediaVaultGridActivity.class);
                intent.putExtra("COLLECTION_ID", col.optString("id"));
                intent.putExtra("COLLECTION_PREFIX", col.optString("key_prefix"));
                startActivity(intent);
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
