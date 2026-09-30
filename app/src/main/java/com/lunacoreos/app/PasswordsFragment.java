package com.lunacoreos.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PasswordsFragment extends Fragment {

    private TextView tvTitle;
    private RecyclerView rvPasswords;
    private SwipeRefreshLayout swipeRefresh;
    private EditText etSearch;
    private PasswordAdapter adapter;
    private List<JSONObject> allPasswordList = new ArrayList<>();
    private List<JSONObject> filteredPasswordList = new ArrayList<>();

    private final ActivityResultLauncher<Intent> vaultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    loadPasswords();
                } else {
                    // Lock failed or cancelled, can't show passwords
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_passwords, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvTitle = view.findViewById(R.id.tvTitle);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        etSearch = view.findViewById(R.id.etSearch);
        rvPasswords = view.findViewById(R.id.rvPasswords);
        rvPasswords.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PasswordAdapter();
        rvPasswords.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> {
            if (CryptoService.hasSessionKey()) {
                loadPasswords();
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filterPasswords(s.toString());
            }
        });

        FloatingActionButton fab = view.findViewById(R.id.fabAddPassword);
        fab.setOnClickListener(v -> {
            if (!CryptoService.hasSessionKey()) {
                vaultLauncher.launch(new Intent(getContext(), VaultLockActivity.class));
            } else {
                startActivity(new Intent(getContext(), PasswordEditorActivity.class));
            }
        });

        // Always check lock on resume/create
        if (!CryptoService.hasSessionKey()) {
            vaultLauncher.launch(new Intent(getContext(), VaultLockActivity.class));
        } else {
            loadPasswords();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (CryptoService.hasSessionKey()) {
            loadPasswords();
        }
    }

    private void loadPasswords() {
        new Thread(() -> {
            try {
                SupabaseClient client = new SupabaseClient(getContext());
                JSONArray arr = client.getPasswords();
                
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getJSONObject(i));
                }

                requireActivity().runOnUiThread(() -> {
                    allPasswordList = list;
                    filterPasswords(etSearch.getText().toString());
                    swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Failed to load passwords: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    swipeRefresh.setRefreshing(false);
                });
            }
        }).start();
    }

    private void filterPasswords(String query) {
        filteredPasswordList.clear();
        if (query.isEmpty()) {
            filteredPasswordList.addAll(allPasswordList);
        } else {
            String q = query.toLowerCase();
            for (JSONObject pwd : allPasswordList) {
                String site = pwd.optString("site_name").toLowerCase();
                String user = pwd.optString("username").toLowerCase();
                if (site.contains(q) || user.contains(q)) {
                    filteredPasswordList.add(pwd);
                }
            }
        }
        
        if (tvTitle != null) {
            tvTitle.setText("Password (" + filteredPasswordList.size() + ")");
        }
        
        adapter.notifyDataSetChanged();
    }

    private class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_password, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject pwd = filteredPasswordList.get(position);
            holder.tvSiteName.setText(pwd.optString("site_name"));
            holder.tvCategory.setText(pwd.optString("category", "General"));
            holder.tvUsername.setText(pwd.optString("username"));
            
            String strength = pwd.optString("strength", "weak").toLowerCase();
            if (strength.equals("weak")) {
                holder.tvStrength.setText("Weak");
                holder.tvStrength.setTextColor(getResources().getColor(R.color.danger, null));
            } else if (strength.equals("fair")) {
                holder.tvStrength.setText("Fair");
                holder.tvStrength.setTextColor(getResources().getColor(android.R.color.holo_orange_light, null));
            } else {
                holder.tvStrength.setText("Strong");
                holder.tvStrength.setTextColor(getResources().getColor(R.color.accent_green, null));
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), PasswordEditorActivity.class);
                intent.putExtra("password_data", pwd.toString());
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                try {
                    String enc = pwd.getString("enc_password");
                    String iv = pwd.getString("enc_iv");
                    String plain = CryptoService.decryptPassword(enc, iv);
                    
                    ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("password", plain);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getContext(), "Password copied! Clears in 30s.", Toast.LENGTH_SHORT).show();

                    // Auto clear after 30s
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        ClipData currentClip = clipboard.getPrimaryClip();
                        if (currentClip != null && currentClip.getItemCount() > 0) {
                            if (plain.equals(currentClip.getItemAt(0).getText().toString())) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                                Toast.makeText(getContext(), "Clipboard cleared", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, 30000);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Decryption failed", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return filteredPasswordList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSiteName, tvCategory, tvUsername, tvPasswordHidden, tvStrength;
            ViewHolder(View itemView) {
                super(itemView);
                tvSiteName = itemView.findViewById(R.id.tvSiteName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                tvUsername = itemView.findViewById(R.id.tvUsername);
                tvPasswordHidden = itemView.findViewById(R.id.tvPasswordHidden);
                tvStrength = itemView.findViewById(R.id.tvStrength);
            }
        }
    }
}
