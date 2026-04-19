package com.emergency.patient.network;

import android.content.Context;

import com.emergency.patient.security.AuthInterceptor;
import com.emergency.patient.security.TokenManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * ApiClient — Singleton Retrofit client.
 *
 * Attaches JWT via AuthInterceptor on every request.
 * Call ApiClient.getInstance(context).create(YourService.class) to use.
 *
 * Backend API base URL must be set in BASE_URL below.
 */
public class ApiClient {

    private static final String BASE_URL = "https://api.emergency-response.com/";
    private static Retrofit instance;

    private ApiClient() {}

    public static synchronized Retrofit getInstance(Context context) {
        if (instance == null) {

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            // Use BODY in debug, NONE in release
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(context))
                    .addInterceptor(logging)
                    .build();

            instance = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return instance;
    }

    /** Call this after a JWT refresh so the interceptor uses the new token. */
    public static void invalidate() {
        instance = null;
    }
}
