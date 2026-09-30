package com.lunacoreos.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lunacoreos.app.writing.WritingListFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            if (item.getItemId() == R.id.nav_writing) {
                selectedFragment = new WritingListFragment();
            } else if (item.getItemId() == R.id.nav_vault) {
                selectedFragment = new MediaVaultFragment();
            } else if (item.getItemId() == R.id.nav_passwords) {
                selectedFragment = new PasswordsFragment();
            } else if (item.getItemId() == R.id.nav_linkbox) {
                selectedFragment = new LinkboxFragment();
            } else if (item.getItemId() == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            }
            
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        // Set default selection
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_writing);
        }

        new Thread(() -> {
            new SupabaseClient(this).refreshSession();
        }).start();
    }
}
