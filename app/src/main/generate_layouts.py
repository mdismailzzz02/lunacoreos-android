import os

base_dir = r'd:\Projects\lunacoreos\lunacoreos-android\app\src\main'
java_dir = os.path.join(base_dir, r'java\com\lunacoreos\app')
res_dir = os.path.join(base_dir, 'res')

os.makedirs(java_dir, exist_ok=True)
os.makedirs(os.path.join(res_dir, 'layout'), exist_ok=True)

# 1. activity_vault_lock.xml
with open(os.path.join(res_dir, 'layout', 'activity_vault_lock.xml'), 'w') as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:fitsSystemWindows="true"
    android:background="@color/bg_dark"
    android:padding="32dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="??"
        android:textSize="64sp"
        android:layout_marginBottom="16dp" />

    <TextView
        android:id="@+id/tvVaultTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Unlock Vault"
        android:textSize="24sp"
        android:textColor="@color/text_primary"
        android:textStyle="bold"
        android:layout_marginBottom="8dp" />

    <TextView
        android:id="@+id/tvVaultDesc"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Enter your master password"
        android:textSize="14sp"
        android:textColor="@color/text_muted"
        android:layout_marginBottom="32dp"
        android:textAlignment="center" />

    <EditText
        android:id="@+id/etMasterPassword"
        android:layout_width="match_parent"
        android:layout_height="52dp"
        android:background="@drawable/input_background"
        android:hint="Master Password"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_hint"
        android:paddingHorizontal="16dp"
        android:inputType="textPassword"
        android:layout_marginBottom="24dp" />

    <Button
        android:id="@+id/btnUnlock"
        android:layout_width="match_parent"
        android:layout_height="52dp"
        android:background="@drawable/btn_primary"
        android:text="Unlock"
        android:textColor="@color/text_primary"
        android:textStyle="bold"
        android:textAllCaps="false" />

    <TextView
        android:id="@+id/tvError"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/danger"
        android:layout_marginTop="16dp"
        android:visibility="gone" />

</LinearLayout>
''')

# 2. fragment_passwords.xml
with open(os.path.join(res_dir, 'layout', 'fragment_passwords.xml'), 'w') as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_dark">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Passwords"
        android:textSize="24sp"
        android:textColor="@color/text_primary"
        android:textStyle="bold"
        android:padding="24dp" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvPasswords"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:paddingHorizontal="16dp"
        android:clipToPadding="false" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAddPassword"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="24dp"
        android:src="@android:drawable/ic_input_add"
        android:backgroundTint="@color/accent_primary"
        android:tint="@color/text_primary" />

</LinearLayout>
''')

# 3. item_password.xml
with open(os.path.join(res_dir, 'layout', 'item_password.xml'), 'w') as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/item_background"
    android:layout_marginBottom="8dp"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">
        <TextView
            android:id="@+id/tvSiteName"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textColor="@color/text_primary"
            android:textSize="18sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/tvCategory"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/accent_primary"
            android:textSize="12sp"
            android:background="@drawable/tag_background"
            android:paddingHorizontal="8dp"
            android:paddingVertical="4dp" />
    </LinearLayout>

    <TextView
        android:id="@+id/tvUsername"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="@color/text_secondary"
        android:textSize="14sp"
        android:layout_marginTop="4dp" />

    <TextView
        android:id="@+id/tvPasswordHidden"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="••••••••••••"
        android:textColor="@color/text_muted"
        android:textSize="14sp"
        android:layout_marginTop="8dp" />

</LinearLayout>
''')

# 4. tag_background.xml
with open(os.path.join(res_dir, 'drawable', 'tag_background.xml'), 'w') as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#33A29BFE" />
    <corners android:radius="8dp" />
</shape>
''')

# 5. activity_password_editor.xml
with open(os.path.join(res_dir, 'layout', 'activity_password_editor.xml'), 'w') as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:fitsSystemWindows="true"
    android:background="@color/bg_dark">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@color/bg_surface"
        android:elevation="4dp"
        android:paddingHorizontal="8dp">

        <ImageButton
            android:id="@+id/btnBack"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_revert"
            app:tint="@color/text_secondary" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Edit Password"
            android:textColor="@color/text_primary"
            android:textSize="18sp"
            android:textStyle="bold"
            android:gravity="center" />

        <Button
            android:id="@+id/btnSave"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Save"
            android:textColor="@color/text_primary"
            android:backgroundTint="@color/accent_primary"
            android:textAllCaps="false" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="24dp">

            <TextView android:text="Site Name" android:textColor="@color/text_secondary" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginBottom="4dp"/>
            <EditText android:id="@+id/etSiteName" android:layout_width="match_parent" android:layout_height="52dp" android:background="@drawable/input_background" android:textColor="@color/text_primary" android:paddingHorizontal="16dp" android:layout_marginBottom="16dp" />

            <TextView android:text="Username / Email" android:textColor="@color/text_secondary" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginBottom="4dp"/>
            <EditText android:id="@+id/etUsername" android:layout_width="match_parent" android:layout_height="52dp" android:background="@drawable/input_background" android:textColor="@color/text_primary" android:paddingHorizontal="16dp" android:layout_marginBottom="16dp" />

            <TextView android:text="Password" android:textColor="@color/text_secondary" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginBottom="4dp"/>
            <EditText android:id="@+id/etPassword" android:layout_width="match_parent" android:layout_height="52dp" android:background="@drawable/input_background" android:textColor="@color/text_primary" android:paddingHorizontal="16dp" android:inputType="textPassword" android:layout_marginBottom="4dp" />
            
            <TextView android:id="@+id/tvStrength" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Strength: Weak" android:textColor="@color/danger" android:textSize="12sp" android:layout_marginBottom="16dp" />

            <TextView android:text="Category" android:textColor="@color/text_secondary" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginBottom="4dp"/>
            <EditText android:id="@+id/etCategory" android:layout_width="match_parent" android:layout_height="52dp" android:background="@drawable/input_background" android:textColor="@color/text_primary" android:paddingHorizontal="16dp" android:layout_marginBottom="16dp" android:text="General" />

            <Button android:id="@+id/btnDelete" android:layout_width="match_parent" android:layout_height="52dp" android:text="Delete Entry" android:textColor="@color/danger" android:background="@android:color/transparent" android:textAllCaps="false" android:layout_marginTop="24dp" />

        </LinearLayout>
    </ScrollView>
</LinearLayout>
''')

print("All layouts created.")
