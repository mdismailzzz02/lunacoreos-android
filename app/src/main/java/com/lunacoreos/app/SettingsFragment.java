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
        
        EditText etEmail = view.findViewById(R.id.etSettingsEmail);
        EditText etPassword = view.findViewById(R.id.etSettingsPassword);
        
        etEmail.setText(prefs.getString("savedEmail", ""));
        etPassword.setText(prefs.getString("savedPassword", ""));

        view.findViewById(R.id.btnSaveCredentials).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            prefs.edit()
                .putString("savedEmail", email)
                .putString("savedPassword", password)
                .apply();
                
            Toast.makeText(getContext(), "Credentials saved for auto-login", Toast.LENGTH_SHORT).show();
            
            new Thread(() -> {
                SupabaseClient client = new SupabaseClient(getContext());
                try {
                    client.login(email, password);
                } catch (Exception ignored) {}
            }).start();
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
