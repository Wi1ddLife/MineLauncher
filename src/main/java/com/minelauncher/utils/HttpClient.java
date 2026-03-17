package com.minelauncher.utils;

import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpClient.class);
    private static final Gson GSON = new Gson();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static String get(String url) throws IOException {
        return get(url, null);
    }

    public static String get(String url, String bearerToken) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return response.body().string();
        }
    }

    public static String post(String url, String jsonBody) throws IOException {
        return post(url, jsonBody, null);
    }

    public static String post(String url, String jsonBody, String bearerToken) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }
            return responseBody;
        }
    }

    public static String postForm(String url, String formBody) throws IOException {
        RequestBody body = RequestBody.create(formBody,
                MediaType.get("application/x-www-form-urlencoded"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }
            return responseBody;
        }
    }

    public static <T> T getJson(String url, Class<T> type) throws IOException {
        return GSON.fromJson(get(url), type);
    }

    public static OkHttpClient getClient() {
        return CLIENT;
    }
}
