package com.emergency.patient.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import java.util.List;
import java.util.Map;

public interface GroqApiService {
    @POST("chat/completions")
    Call<GroqResponse> getChatCompletion(
        @Header("Authorization") String authHeader,
        @Body GroqRequest request
    );

    class GroqRequest {
        public String model;
        public List<Message> messages;

        public GroqRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
        }

        public static class Message {
            public String role;
            public String content;

            public Message(String role, String content) {
                this.role = role;
                this.content = content;
            }
        }
    }

    class GroqResponse {
        public List<Choice> choices;

        public static class Choice {
            public Message message;
        }

        public static class Message {
            public String content;
        }
    }
}
