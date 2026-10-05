package com.lunacoreos.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.text.SimpleDateFormat;

public class PhoneSyncFragment extends Fragment {

    private SupabaseClient client;
    private Button tabCalls, tabContacts, tabSms;
    private LinearLayout listContainer;
    private EditText searchInput;
    private ImageView clearSearchBtn;
    private Spinner dateFilterSpinner;
    
    private String activeTab = "calls";
    private String searchQuery = "";
    private String dateFilter = "all";
    
    private List<JSONObject> callsList = new ArrayList<>();
    private List<JSONObject> contactsList = new ArrayList<>();
    private List<JSONObject> smsList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_phone_sync, container, false);

        client = new SupabaseClient(requireContext());

        tabCalls = view.findViewById(R.id.tabCalls);
        tabContacts = view.findViewById(R.id.tabContacts);
        tabSms = view.findViewById(R.id.tabSms);
        listContainer = view.findViewById(R.id.listContainer);
        searchInput = view.findViewById(R.id.searchInput);
        clearSearchBtn = view.findViewById(R.id.clearSearchBtn);

        tabCalls.setOnClickListener(v -> setActiveTab("calls"));
        tabContacts.setOnClickListener(v -> setActiveTab("contacts"));
        tabSms.setOnClickListener(v -> setActiveTab("sms"));

        dateFilterSpinner = view.findViewById(R.id.dateFilterSpinner);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"All Time", "Today", "Yesterday"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dateFilterSpinner.setAdapter(spinnerAdapter);
        dateFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) dateFilter = "all";
                else if (position == 1) dateFilter = "today";
                else if (position == 2) dateFilter = "yesterday";
                renderList();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        clearSearchBtn.setOnClickListener(v -> {
            searchInput.setText("");
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase();
                clearSearchBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                renderList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadData();
        return view;
    }

    private void setActiveTab(String tab) {
        this.activeTab = tab;
        tabCalls.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(tab.equals("calls") ? "#3b82f6" : "#2d2d2d")));
        tabContacts.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(tab.equals("contacts") ? "#3b82f6" : "#2d2d2d")));
        tabSms.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(tab.equals("sms") ? "#3b82f6" : "#2d2d2d")));
        dateFilterSpinner.setVisibility(tab.equals("contacts") ? View.GONE : View.VISIBLE);
        renderList();
    }

    private void loadData() {
        new Thread(() -> {
            try {
                JSONArray callsRes = client.fetchTable("phone_call_logs", "*", "timestamp.desc", 200);
                JSONArray contactsRes = client.fetchTable("phone_contacts", "*", "display_name.asc", 0);
                JSONArray smsRes = client.fetchTable("phone_sms", "*", "timestamp.desc", 200);
                
                callsList.clear(); contactsList.clear(); smsList.clear();
                for (int i=0; i<callsRes.length(); i++) callsList.add(callsRes.getJSONObject(i));
                for (int i=0; i<contactsRes.length(); i++) contactsList.add(contactsRes.getJSONObject(i));
                for (int i=0; i<smsRes.length(); i++) smsList.add(smsRes.getJSONObject(i));
                
                requireActivity().runOnUiThread(this::renderList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void renderList() {
        listContainer.removeAllViews();
        List<JSONObject> current = activeTab.equals("calls") ? callsList : activeTab.equals("contacts") ? contactsList : smsList;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");

        for (JSONObject item : current) {
            String searchText = item.toString().toLowerCase();
            if (!searchQuery.isEmpty() && !searchText.contains(searchQuery)) {
                continue; // filter
            }
            
            if (!activeTab.equals("contacts") && !dateFilter.equals("all")) {
                long ts = 0;
                String tsStr = item.has("timestamp") ? item.optString("timestamp", "0") : item.optString("date", "0");
                try {
                    ts = Long.parseLong(tsStr);
                } catch (NumberFormatException e) {
                    try {
                        ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(tsStr).getTime();
                    } catch (Exception ex) {}
                }
                
                if (ts > 0) {
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
                    String itemDateStr = fmt.format(new Date(ts));
                    
                    String todayStr = fmt.format(new Date());
                    if (dateFilter.equals("today") && !itemDateStr.equals(todayStr)) continue;
                    
                    String yesterdayStr = fmt.format(new Date(System.currentTimeMillis() - 86400000L));
                    if (dateFilter.equals("yesterday") && !itemDateStr.equals(yesterdayStr)) continue;
                }
            }

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(32, 32, 32, 32);
            row.setBackgroundResource(R.drawable.menu_item_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            row.setLayoutParams(lp);

            TextView title = new TextView(getContext());
            title.setTextColor(android.graphics.Color.WHITE);
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView subtitle = new TextView(getContext());
            subtitle.setTextColor(android.graphics.Color.parseColor("#888888"));
            subtitle.setTextSize(14);
            
            try {
                if (activeTab.equals("calls")) {
                    String name = item.optString("cached_name", "");
                    if (name.isEmpty() || name.equals("null")) name = "Unknown Contact";
                    String num = item.optString("number", "");
                    title.setText(name + (num.isEmpty() ? "" : " - " + num));
                    
                    String typeStr = item.optString("type", "");
                    String typeName = typeStr.equals("1") ? "Incoming" : typeStr.equals("2") ? "Outgoing" : typeStr.equals("3") ? "Missed" : "Unknown";
                    long ts = 0;
                    String tsStr = item.optString("timestamp", "0");
                    try {
                        ts = Long.parseLong(tsStr);
                    } catch (Exception e) {
                        try {
                            ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(tsStr).getTime();
                        } catch (Exception ex) {}
                    }
                    subtitle.setText(typeName + " • " + (ts > 0 ? sdf.format(new Date(ts)) : ""));
                } else if (activeTab.equals("contacts")) {
                    String name = item.optString("display_name", "Unknown");
                    title.setText(name);
                    JSONArray phones = item.optJSONArray("phones");
                    if (phones != null && phones.length() > 0) {
                        subtitle.setText(phones.getString(0));
                    }
                } else {
                    title.setText(item.optString("address", "Unknown"));
                    subtitle.setText(item.optString("body", ""));
                }
            } catch (Exception e) {}

            row.addView(title);
            row.addView(subtitle);
            listContainer.addView(row);
        }
    }
}
