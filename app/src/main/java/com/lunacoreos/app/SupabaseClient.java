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

    public SupabaseClient(String url, String key) {
        this.baseUrl = url;
        this.apiKey = key;
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public String login(String email, String password) throws Exception {
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
            return this.authToken;
        } else {
            InputStream es = conn.getErrorStream();
            Scanner s = new Scanner(es).useDelimiter("\\A");
            String err = s.hasNext() ? s.next() : "";
            if (es != null) es.close();
            throw new Exception("Login failed: " + err);
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
}
