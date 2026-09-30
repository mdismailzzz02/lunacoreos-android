package com.lunacoreos.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PasswordsFragment extends Fragment {

    private RecyclerView rvPasswords;
    private PasswordAdapter adapter;
    private List<JSONObject> passwordList = new ArrayList<>();

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
        rvPasswords = view.findViewById(R.id.rvPasswords);
        rvPasswords.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PasswordAdapter();
        rvPasswords.setAdapter(adapter);

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
                    passwordList = list;
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Failed to load passwords", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
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
            JSONObject pwd = passwordList.get(position);
            holder.tvSiteName.setText(pwd.optString("site_name"));
            holder.tvCategory.setText(pwd.optString("category", "General"));
            holder.tvUsername.setText(pwd.optString("username"));

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
            return passwordList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSiteName, tvCategory, tvUsername, tvPasswordHidden;
            ViewHolder(View itemView) {
                super(itemView);
                tvSiteName = itemView.findViewById(R.id.tvSiteName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                tvUsername = itemView.findViewById(R.id.tvUsername);
                tvPasswordHidden = itemView.findViewById(R.id.tvPasswordHidden);
            }
        }
    }
}
