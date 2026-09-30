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

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        Button btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            requireContext().getSharedPreferences("LunaCorePrefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("authToken")
                    .remove("refreshToken")
                    .apply();
            
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
        });
        
        return view;
    }
}
