package com.lunacoreos.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lunacoreos.app.writing.WritingListFragment;

public class MainActivity extends AppCompatActivity {

    private android.content.BroadcastReceiver logoutReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Listen for force-logout broadcasts (expired token)
        logoutReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                android.widget.Toast.makeText(context, "Session expired. Please log in again.", android.widget.Toast.LENGTH_LONG).show();
                android.content.Intent loginIntent = new android.content.Intent(MainActivity.this, LoginActivity.class);
                loginIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(loginIntent);
                finish();
            }
        };
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(this)
                .registerReceiver(logoutReceiver, new android.content.IntentFilter(SupabaseClient.ACTION_FORCE_LOGOUT));

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
            try {
                new SupabaseClient(this).refreshSession();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logoutReceiver != null) {
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                    .getInstance(this)
                    .unregisterReceiver(logoutReceiver);
        }
    }
}
