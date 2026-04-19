package com.emergency.patient.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import java.util.Map;

/**
 * ApiService — Retrofit interface for REST endpoints.
 */
public interface ApiService {

    /**
     * Uploads the FCM device token to the backend for push notifications.
     */
    @POST("patient/update-fcm-token")
    Call<Void> uploadFcmToken(@Body Map<String, String> body);

    /**
     * Triggers the Make.com webhook for the "Call All" emergency feature.
     */
    @POST("2b9qvf5zvmu99zss88yemjgl11irj4nw")
    Call<Void> triggerCallAllWebhook(@Body Map<String, Object> body);

    /**
     * Triggers the Make.com webhook for ambulance booking.
     */
    @POST("https://hook.eu2.make.com/qc7vltpprummrcvn69ybvibfvyet4xvw")
    Call<Void> triggerAmbulanceBookingWebhook(@Body Map<String, Object> body);

}


