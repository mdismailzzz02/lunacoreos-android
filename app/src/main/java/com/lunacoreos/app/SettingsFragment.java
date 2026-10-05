package com.lunacoreos.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.EditText;
import android.widget.Toast;
import android.content.SharedPreferences;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        SharedPreferences prefs = requireContext().getSharedPreferences("LunaCorePrefs", Context.MODE_PRIVATE);
        
        EditText etUrl = view.findViewById(R.id.etSupabaseUrl);
        EditText etKey = view.findViewById(R.id.etSupabaseKey);
        EditText etR2 = view.findViewById(R.id.etR2PublicUrl);
        EditText etTrash = view.findViewById(R.id.etTrashPath);
        
        String savedUrl = prefs.getString("supabaseUrl", "");
        if (savedUrl.isEmpty()) savedUrl = getString(R.string.default_supabase_url);
        etUrl.setText(savedUrl);
        
        String savedKey = prefs.getString("supabaseKey", "");
        if (savedKey.isEmpty()) savedKey = getString(R.string.default_supabase_key);
        etKey.setText(savedKey);
        
        String savedR2 = prefs.getString("r2PublicUrl", "");
        if (savedR2.isEmpty()) savedR2 = getString(R.string.default_r2_url);
        etR2.setText(savedR2);
        
        etTrash.setText(prefs.getString("trashPath", "vault/67539ee2-a1b0-405d-bbc1-c33dcbd198e6/documents-trash/"));

        view.findViewById(R.id.btnSaveCredentials).setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            String key = etKey.getText().toString().trim();
            String r2 = etR2.getText().toString().trim();
            String trash = etTrash.getText().toString().trim();
            
            prefs.edit()
                .putString("supabaseUrl", url)
                .putString("supabaseKey", key)
                .putString("r2PublicUrl", r2)
                .putString("trashPath", trash)
                .apply();
                
            Toast.makeText(getContext(), "Backend settings saved", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            prefs.edit()
                .remove("refreshToken")
                .remove("authToken")
                .remove("savedPassword") // Intentionally leaving savedEmail if they logout
                .apply();
            
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
        });

        return view;
    }
}
