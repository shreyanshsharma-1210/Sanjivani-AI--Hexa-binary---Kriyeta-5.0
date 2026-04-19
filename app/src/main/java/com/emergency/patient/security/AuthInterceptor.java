package com.emergency.patient.security;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import android.content.Context;

/**
 * AuthInterceptor — OkHttp interceptor that attaches JWT to every outgoing request.
 * Added to the Retrofit OkHttpClient in ApiClient.
 */
public class AuthInterceptor implements Interceptor {
    private final Context context;

    public AuthInterceptor(Context context) {
        this.context = context;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        // Fetch latest JWT dynamically
        String jwt = TokenManager.getJWT(context);

        // Skip if JWT is missing
        if (jwt == null || jwt.isEmpty()) {
            return chain.proceed(original);
        }

        Request authenticated = original.newBuilder()
                .header("Authorization", "Bearer " + jwt)
                .build();
        return chain.proceed(authenticated);
    }
}
