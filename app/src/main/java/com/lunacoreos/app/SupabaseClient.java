package com.lunacoreos.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class SupabaseClient {
    public static final String ACTION_FORCE_LOGOUT = "com.lunacoreos.app.FORCE_LOGOUT";

    private final String baseUrl;
    private final String apiKey;
    private String authToken;

    private android.content.SharedPreferences prefs;
    private android.content.Context context;

    public SupabaseClient(String url, String key) {
        this.baseUrl = url;
        this.apiKey = key;
    }

    public SupabaseClient(android.content.Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("LunaCorePrefs", android.content.Context.MODE_PRIVATE);
        this.baseUrl = prefs.getString("supabaseUrl", "");
        this.apiKey = prefs.getString("supabaseKey", "");
        this.authToken = prefs.getString("authToken", null);
    }

    /**
     * Called when any API returns 401. Tries refresh once, then force-logouts.
     * Returns true if refresh succeeded (caller should retry), false if logout was triggered.
     */
    private boolean handleUnauthorized() {
        if (prefs == null) return false;
        try {
            refreshSession();
            return true; // refresh succeeded, caller can retry
        } catch (Exception e) {
            // Refresh also failed — force logout
            prefs.edit()
                .remove("authToken")
                .remove("refreshToken")
                .apply();
            if (context != null) {
                android.content.Intent intent = new android.content.Intent(ACTION_FORCE_LOGOUT);
                androidx.localbroadcastmanager.content.LocalBroadcastManager
                        .getInstance(context)
                        .sendBroadcast(intent);
            }
            return false;
        }
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
    
    public void refreshSession() throws Exception {
        if (prefs == null) throw new Exception("No prefs");
        String refreshToken = prefs.getString("refreshToken", "");
        if (!refreshToken.isEmpty()) {
            try {
                refreshToken(refreshToken);
                return;
            } catch (Exception e) {
                // refresh token failed, try credentials below
            }
        }
        
        String savedEmail = prefs.getString("savedEmail", "");
        String savedPassword = prefs.getString("savedPassword", "");
        if (!savedEmail.isEmpty() && !savedPassword.isEmpty()) {
            JSONObject json = login(savedEmail, savedPassword);
            prefs.edit()
                 .putString("authToken", json.getString("access_token"))
                 .putString("refreshToken", json.optString("refresh_token", ""))
                 .apply();
            return;
        }
        throw new Exception("No credentials to refresh session");
    }



    public String getUserId() {
        if (authToken == null) return null;
        try {
            String[] parts = authToken.split("\\.");
            if (parts.length > 1) {
                String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE), "UTF-8");
                return new JSONObject(payload).getString("sub");
            }
        } catch (Exception e) {}
        return null;
    }

    public JSONObject getAppPasswordV2(String id) throws Exception {
        String userId = getUserId();
        if (userId == null) return null;
        
        // 1. Try namespaced ID
        String finalId = id + "_" + userId;
        URL url = new URL(baseUrl + "/rest/v1/app_passwords_v2?id=eq." + java.net.URLEncoder.encode(finalId, "UTF-8"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) conn.setRequestProperty("Authorization", "Bearer " + authToken);
        
        if (conn.getResponseCode() == 200) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            JSONArray arr = new JSONArray(result);
            if (arr.length() > 0) return arr.getJSONObject(0);
        }

        // 2. Try legacy ID
        url = new URL(baseUrl + "/rest/v1/app_passwords_v2?id=eq." + java.net.URLEncoder.encode(id, "UTF-8"));
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) conn.setRequestProperty("Authorization", "Bearer " + authToken);
        
        if (conn.getResponseCode() == 200) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            JSONArray arr = new JSONArray(result);
            if (arr.length() > 0) return arr.getJSONObject(0);
        }
        return null;
    }

    public void setAppPasswordV2(String id, String label, String salt, String hash) throws Exception {
        String userId = getUserId();
        if (userId == null) throw new Exception("Not authenticated");
        
        // Check if legacy ID exists
        String finalId = id + "_" + userId;
        JSONObject legacy = null;
        
        URL url = new URL(baseUrl + "/rest/v1/app_passwords_v2?id=eq." + java.net.URLEncoder.encode(id, "UTF-8") + "&select=id");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) conn.setRequestProperty("Authorization", "Bearer " + authToken);
        if (conn.getResponseCode() == 200) {
            InputStream is = conn.getInputStream();
            Scanner s = new Scanner(is).useDelimiter("\\A");
            JSONArray arr = new JSONArray(s.hasNext() ? s.next() : "[]");
            is.close();
            if (arr.length() > 0) legacy = arr.getJSONObject(0);
        }
        
        if (legacy != null) finalId = id; // use legacy if it exists

        JSONObject payload = new JSONObject();
        payload.put("id", finalId);
        payload.put("label", label);
        payload.put("salt", salt);
        payload.put("hash", hash);

        URL postUrl = new URL(baseUrl + "/rest/v1/app_passwords_v2");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setRequestProperty("apikey", apiKey);
        postConn.setRequestProperty("Prefer", "resolution=merge-duplicates"); // upsert
        if (authToken != null) postConn.setRequestProperty("Authorization", "Bearer " + authToken);
        postConn.setRequestProperty("Content-Type", "application/json");
        postConn.setDoOutput(true);

        try (OutputStream os = postConn.getOutputStream()) {
            os.write(payload.toString().getBytes("UTF-8"));
        }

        if (postConn.getResponseCode() >= 400) {
            throw new Exception("Failed to set app password: " + postConn.getResponseCode());
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

    public JSONArray getVaultCollections(String mode) throws Exception {
        String urlString = baseUrl + "/rest/v1/vault_collections?order=created_at.desc";
        
        if ("normal".equals(mode)) {
            urlString += "&is_hidden=eq.false&or=%28is_secret.eq.false%2Cis_secret.is.null%29";
        } else if ("hidden".equals(mode)) {
            urlString += "&or=%28is_secret.eq.false%2Cis_secret.is.null%29";
        } else if ("secret".equals(mode)) {
            urlString += "&is_hidden=eq.false";
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
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            is.close();
            return new JSONArray(result);
        } else {
            throw new Exception("Failed to fetch vault collections: " + conn.getResponseCode());
        }
    }

    public JSONObject getTrashCollection() throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_collections?name=eq.Trash&is_hidden=eq.false&limit=1");
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
            JSONArray arr = new JSONArray(result);
            if (arr.length() > 0) {
                return arr.getJSONObject(0);
            }
            return null;
        } else {
            throw new Exception("Failed to fetch trash collection: " + conn.getResponseCode());
        }
    }

    public JSONArray getVaultSubCollections(String parentId) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_collections?parent_id=eq." + parentId + "&order=created_at.desc");
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
            throw new Exception("Failed to fetch sub-collections: " + conn.getResponseCode());
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

    public void deleteVaultCollection(String id) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_collections?id=eq." + id);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to delete vault collection: " + conn.getResponseCode());
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

    public void deleteVaultFile(String fileId) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_files?id=eq." + fileId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to delete vault file: " + conn.getResponseCode());
        }
    }
    
    public void moveVaultFile(String fileId, String newCollectionId, String newR2Key) throws Exception {
        // 1. Fetch current row
        URL getUrl = new URL(baseUrl + "/rest/v1/vault_files?id=eq." + fileId + "&select=*");
        HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
        getConn.setRequestMethod("GET");
        getConn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            getConn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        
        String result;
        try (InputStream is = getConn.getInputStream(); java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
            result = s.hasNext() ? s.next() : "";
        }
        
        JSONArray arr = new JSONArray(result);
        if (arr.length() == 0) throw new Exception("File not found");
        JSONObject row = arr.getJSONObject(0);
        
        // 2. Modify row
        row.put("collection_id", newCollectionId);
        row.put("r2_key", newR2Key);
        
        // 3. Upsert full row
        URL url = new URL(baseUrl + "/rest/v1/vault_files");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(row.toString().getBytes("UTF-8"));
        }
        
        if (conn.getResponseCode() >= 400) {
            String errBody = "";
            try (InputStream es = conn.getErrorStream(); java.util.Scanner s = new java.util.Scanner(es).useDelimiter("\\A")) {
                errBody = s.hasNext() ? s.next() : "";
            } catch (Exception ignored) {}
            throw new Exception("DB Error 400: " + errBody);
        }
    }
    
    public void deleteR2File(String r2Key) throws Exception {
        URL url = new URL(baseUrl + "/functions/v1/r2-presign?op=delete&key=" + java.net.URLEncoder.encode(r2Key, "UTF-8"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        
        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to delete from R2: " + conn.getResponseCode());
        }
    }
    
    public void moveR2File(String sourceKey, String destKey) throws Exception {
        URL url = new URL(baseUrl + "/functions/v1/r2-presign?op=copy&source_key=" + java.net.URLEncoder.encode(sourceKey, "UTF-8") + "&dest_key=" + java.net.URLEncoder.encode(destKey, "UTF-8"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        
        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to move file in R2: " + conn.getResponseCode());
        }
    }

    public JSONArray getVaultFiles(String collectionId, int limit, int offset, boolean favoritesOnly) throws Exception {
        String urlStr = baseUrl + "/rest/v1/vault_files?collection_id=eq." + collectionId + "&order=uploaded_at.desc&limit=" + limit + "&offset=" + offset;
        if (favoritesOnly) {
            urlStr += "&select=*,vault_liked_files!inner(id)";
        } else {
            urlStr += "&select=*,vault_liked_files(id)";
        }
        URL url = new URL(urlStr);
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
            InputStream es = conn.getErrorStream();
            String err = "";
            if (es != null) {
                java.util.Scanner s = new java.util.Scanner(es).useDelimiter("\\A");
                err = s.hasNext() ? s.next() : "";
                es.close();
            }
            throw new Exception("Code " + conn.getResponseCode() + ": " + err);
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

    public void toggleFileLike(String fileId, boolean like) throws Exception {
        URL url = new URL(baseUrl + "/rest/v1/vault_liked_files" + (!like ? "?file_id=eq." + fileId : ""));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(like ? "POST" : "DELETE");
        conn.setRequestProperty("apikey", apiKey);
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        
        if (like) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
            conn.setDoOutput(true);
            JSONObject payload = new JSONObject();
            payload.put("file_id", fileId);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes("UTF-8"));
            }
        }
        
        if (conn.getResponseCode() >= 400) {
            throw new Exception("Failed to toggle like: " + conn.getResponseCode());
        }
    }
    public void saveClipboardText(String content, boolean isSecret) throws Exception {
        if (authToken == null) throw new Exception("Not authenticated");

        org.json.JSONObject payload = new org.json.JSONObject();
        payload.put("content", content);
        payload.put("is_secret", isSecret);

        java.net.URL url = new java.net.URL(baseUrl + "/rest/v1/vault_clipboard");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", apiKey);
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Prefer", "return=minimal");
        conn.setDoOutput(true);

        java.io.OutputStream os = conn.getOutputStream();
        os.write(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        os.close();

        int code = conn.getResponseCode();
        if (code >= 400) {
            java.io.InputStream errStr = conn.getErrorStream();
            if (errStr != null) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(errStr));
                StringBuilder errBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) errBody.append(line);
                br.close();
                throw new Exception("HTTP " + code + ": " + errBody.toString());
            }
            throw new Exception("HTTP " + code);
        }
    }
}
