package com.lunacoreos.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class SupabaseClient {
    private final String baseUrl;
    private final String apiKey;
    private String authToken;

    private android.content.SharedPreferences prefs;

    public SupabaseClient(String url, String key) {
        this.baseUrl = url;
        this.apiKey = key;
    }

    public SupabaseClient(android.content.Context context) {
        this.prefs = context.getSharedPreferences("LunaCorePrefs", android.content.Context.MODE_PRIVATE);
        this.baseUrl = prefs.getString("supabaseUrl", "");
        this.apiKey = prefs.getString("supabaseKey", "");
        this.authToken = prefs.getString("authToken", null);
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public JSONObject login(String email, String password) throws Exception {
        URL url = new URL(baseUrl + "/auth/v1/token?grant_type=password");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject payload = new JSONObject();
        payload.put("email", email);
        payload.put("password", password);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            JSONObject json = new JSONObject(result);
            this.authToken = json.getString("access_token");
            return json;
        } else {
            InputStream es = conn.getErrorStream();
            Scanner s = new Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Login failed: " + err);
        }
    }
    
    public void refreshSession() {
        if (prefs == null) return;
        String refreshToken = prefs.getString("refreshToken", "");
        if (!refreshToken.isEmpty()) {
            try {
                refreshToken(refreshToken);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        String savedEmail = prefs.getString("savedEmail", "");
        String savedPassword = prefs.getString("savedPassword", "");
        if (!savedEmail.isEmpty() && !savedPassword.isEmpty()) {
            try {
                JSONObject json = login(savedEmail, savedPassword);
                prefs.edit()
                     .putString("authToken", json.getString("access_token"))
                     .putString("refreshToken", json.optString("refresh_token", ""))
                     .apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void refreshToken(String refreshToken) throws Exception {
        URL url = new URL(baseUrl + "/auth/v1/token?grant_type=refresh_token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject payload = new JSONObject();
        payload.put("refresh_token", refreshToken);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            JSONObject json = new JSONObject(result);
            this.authToken = json.getString("access_token");
            
            if (prefs != null) {
                prefs.edit()
                     .putString("authToken", this.authToken)
                     .putString("refreshToken", json.optString("refresh_token", ""))
                     .apply();
            }
        } else {
            InputStream es = conn.getErrorStream();
            Scanner s = new Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Refresh failed: " + err);
        }
    }

    public JSONArray getWritings(String mode) throws Exception {
        // filter by mode, order by updatedAt desc
        String urlString = baseUrl + "/rest/v1/writing?order=updatedAt.desc";
        if ("normal".equals(mode)) {
            urlString += "&or=(mode.eq.normal,mode.is.null)";
        } else {
            urlString += "&mode=eq." + mode;
        }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            return new JSONArray(result);
        } else {
            throw new Exception("Failed to fetch writings: " + conn.getResponseCode());
        }
    }

    public void saveWriting(JSONObject draft) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/writing?on_conflict=id");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(draft.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() >= 400) {
            InputStream es = conn.getErrorStream();
            Scanner s = new Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Failed to save: " + err);
        }
    }

    public void deleteWriting(String id) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/writing?id=eq." + id);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to delete: " + conn.getResponseCode());
        }
    }

    public JSONArray getPasswords() throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/passwords?order=updated_at.desc");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            return new JSONArray(result);
        } else {
            throw new Exception("Failed to fetch passwords: " + conn.getResponseCode());
        }
    }

    public void savePassword(JSONObject password) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/passwords?on_conflict=id");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(password.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() >= 400) {
            InputStream es = conn.getErrorStream();
            Scanner s = new Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Failed to save password: " + err);
        }
    }

    public void deletePassword(String id) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/passwords?id=eq." + id);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to delete password: " + conn.getResponseCode());
        }
    }

    public JSONArray getLinkboxEntries() throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/linkbox?order=created_at.desc");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            return new JSONArray(result);
        } else {
            throw new Exception("Failed to fetch linkbox: " + conn.getResponseCode());
        }
    }

    public void saveLinkboxEntry(JSONObject entry) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/linkbox?on_conflict=id");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(entry.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() >= 400) {
            InputStream es = conn.getErrorStream();
            Scanner s = new Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Failed to save linkbox: " + err);
        }
    }

    public void deleteLinkboxEntry(String id) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/linkbox?id=eq." + id);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to delete linkbox: " + conn.getResponseCode());
        }
    }

    public JSONArray getVaultCollections() throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_collections?order=created_at.desc");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            return new JSONArray(result);
        } else {
            throw new Exception("Failed to fetch vault collections: " + conn.getResponseCode());
        }
    }

    public String getR2PresignedPutUrl(String r2Key, String mimeType) throws Exception {
        URL url = new URL(baseUrl + "/functions/v1/r2-presign?op=put&key=" + java.net.URLEncoder.encode(r2Key, "UTF-8") + "&content_type=" + java.net.URLEncoder.encode(mimeType, "UTF-8"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("apikey", apiKey);

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            JSONObject obj = new JSONObject(result);
            return obj.optString("url");
        } else {
            throw new Exception("Failed to get presigned URL: " + conn.getResponseCode());
        }
    }

    public void createVaultCollection(JSONObject collection) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_collections");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(collection.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() >= 400) {
            InputStream es = conn.getErrorStream();
            java.util.Scanner s = new java.util.Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Failed to create vault collection: " + err);
        }
    }

    public void saveVaultFile(JSONObject fileData) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_files");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(fileData.toString().getBytes("UTF-8"));
        }

        if (conn.getResponseCode() >= 400) {
            InputStream es = conn.getErrorStream();
            java.util.Scanner s = new java.util.Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Failed to save vault file: " + err);
        }
    }

    public JSONArray getVaultFiles(String collectionId) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_files?collection_id=eq." + collectionId + "&order=created_at.desc");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            InputStream is = conn.getInputStream();
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            return new JSONArray(result);
        } else {
            throw new Exception("Failed to fetch vault files: " + conn.getResponseCode());
        }
    }

    public String getR2PublicUrl(String key) {
        String publicBase = prefs.getString("r2PublicUrl", "");
        if (publicBase.isEmpty()) return null;
        if (publicBase.endsWith("/")) publicBase = publicBase.substring(0, publicBase.length() - 1);
        try {
            return publicBase + "/" + key.replace(" ", "%20"); // simple encode
        } catch (Exception e) {
            return null;
        }
    }
}
