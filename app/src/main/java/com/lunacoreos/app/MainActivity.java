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

        // Listen for force-logout broadcasts (expired/invalid token)
        logoutReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                android.widget.Toast.makeText(context, "Session expired. Please sign in again.", android.widget.Toast.LENGTH_LONG).show();
                // Clear tokens so LoginActivity shows the login form
                getSharedPreferences("LunaCorePrefs", android.content.Context.MODE_PRIVATE)
                        .edit().remove("authToken").remove("refreshToken").apply();
                android.content.Intent loginIntent = new android.content.Intent(MainActivity.this, LoginActivity.class);
                loginIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(loginIntent);
                finish();
            }
        };
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(this)
                .registerReceiver(logoutReceiver, new android.content.IntentFilter(SupabaseClient.ACTION_FORCE_LOGOUT));

        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        com.google.android.material.navigation.NavigationView navView = findViewById(R.id.navView);
        
        navView.setNavigationItemSelectedListener(item -> {
            drawerLayout.close();
            Fragment selectedFragment = null;
            if (item.getItemId() == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            } else if (item.getItemId() == R.id.nav_linkbox) {
                selectedFragment = new LinkboxFragment();
            } else if (item.getItemId() == R.id.nav_logout) {
                // Clear tokens so next launch shows login screen
                getSharedPreferences("LunaCorePrefs", android.content.Context.MODE_PRIVATE)
                        .edit().remove("authToken").remove("refreshToken").apply();
                android.content.Intent loginIntent = new android.content.Intent(MainActivity.this, LoginActivity.class);
                loginIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(loginIntent);
                finish();
                return true;
            }
            
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, selectedFragment)
                        .commit();
            }
            return true;
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_more) {
                drawerLayout.open();
                return false; // Don't check the More icon
            }
            
            Fragment selectedFragment = null;
            if (item.getItemId() == R.id.nav_writing) {
                selectedFragment = new WritingListFragment();
            } else if (item.getItemId() == R.id.nav_vault) {
                selectedFragment = new MediaVaultFragment();
            } else if (item.getItemId() == R.id.nav_passwords) {
                selectedFragment = new PasswordsFragment();
            } else if (item.getItemId() == R.id.nav_clipboard) {
                selectedFragment = new ClipboardFragment();
            }
            
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            private int backPressCount = 0;
            private long lastBackPressTime = 0;

            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isOpen()) {
                    drawerLayout.close();
                    return;
                }
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBackPressTime > 2000) {
                    backPressCount = 0;
                }
                
                backPressCount++;
                lastBackPressTime = currentTime;

                if (backPressCount >= 3) {
                    backPressCount = 0;
                    new android.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("Exit LunaCoreOS")
                            .setMessage("Are you sure you want to leave the app?")
                            .setPositiveButton("Leave", (dialog, which) -> finishAffinity())
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    android.widget.Toast.makeText(MainActivity.this, "Swipe back " + (3 - backPressCount) + " more times to exit", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Set default selection
        if (savedInstanceState == null) {
            int targetTab = getIntent().getIntExtra("TARGET_TAB", R.id.nav_writing);
            bottomNav.setSelectedItemId(targetTab);
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

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra("TARGET_TAB")) {
            com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
            bottomNav.setSelectedItemId(intent.getIntExtra("TARGET_TAB", R.id.nav_writing));
        }
    }
}
