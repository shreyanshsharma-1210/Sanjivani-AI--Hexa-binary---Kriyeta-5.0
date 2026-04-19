package com.emergency.patient.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.ChatMessageEntity;
import com.emergency.patient.network.GroqApiService;
import com.emergency.patient.rag.HealthKnowledgeBase;
import com.emergency.patient.rag.RagManager;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.db.PatientEntity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HealthAssistantFragment extends Fragment {

    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend;
    private ProgressBar progressLoading;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private GroqApiService groqService;

    // TODO: Replace with your Groq API Key
    private String GROQ_API_KEY = "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";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_health_assistant, container, false);

        rvChat = view.findViewById(R.id.rv_chat);
        etMessage = view.findViewById(R.id.et_message);
        btnSend = view.findViewById(R.id.btn_send);
        progressLoading = view.findViewById(R.id.progress_loading);

        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChat.setAdapter(adapter);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.groq.com/openai/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        groqService = retrofit.create(GroqApiService.class);

        btnSend.setOnClickListener(v -> sendMessage());
        
        loadChatHistory();

        return view;
    }

    private void loadChatHistory() {
        String patientUuid = TokenManager.getUUID(getContext());
        new Thread(() -> {
            AppDatabase db = AppDatabaseProvider.getInstance(getContext());
            List<ChatMessageEntity> history = db.chatMessageDao().getMessagesForPatient(patientUuid);
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (history.isEmpty()) {
                        addMessage("AI", "Hello! I am your Health Assistant. I can help you understand your medical reports and health records. How can I assist you today?");
                    } else {
                        for (ChatMessageEntity msg : history) {
                            messages.add(new ChatMessage(msg.sender, msg.content));
                        }
                        adapter.notifyDataSetChanged();
                        rvChat.scrollToPosition(messages.size() - 1);
                    }
                });
            }
        }).start();
    }

    private void sendMessage() {
        String userQuery = etMessage.getText().toString().trim();
        if (userQuery.isEmpty()) return;

        addMessage("You", userQuery);
        etMessage.setText("");
        
        String patientUuid = TokenManager.getUUID(getContext());
        
        new Thread(() -> {
            if (getContext() == null) return;
            AppDatabase db = AppDatabaseProvider.getInstance(getContext());
            PatientEntity patient = db.patientDao().getPatient(patientUuid);
            
            // 1. Get standardized Q&A context
            String qaContext = HealthKnowledgeBase.getRelevantContext(userQuery);
            
            // 2. Get document-based context (Secondary fallback)
            List<String> contextChunks = RagManager.getRelevantContext(getContext(), patientUuid, userQuery);
            
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                StringBuilder contextBuilder = new StringBuilder();
                
                // Add Patient Profile Info
                contextBuilder.append("Patient Profile Information:\n");
                if (patient != null) {
                    contextBuilder.append("- Name: ").append(patient.fullName).append("\n");
                    contextBuilder.append("- Blood Group: ").append(patient.bloodGroup).append("\n");
                    contextBuilder.append("- Allergies: ").append(patient.clinicalAllergies).append("\n");
                    contextBuilder.append("- Past Medical History: ").append(patient.pastMedicalDiagnosis).append("\n");
                    contextBuilder.append("- Hereditary: ").append(patient.hereditaryConditions).append("\n");
                    contextBuilder.append("- Lifestyle: ").append(patient.lifestyleFactor).append("\n");
                }
                contextBuilder.append("\n");

                // Add Standardized Q&A (Prime Source)
                if (!qaContext.isEmpty()) {
                    contextBuilder.append("Standardized Health Knowledge:\n").append(qaContext).append("\n");
                }

                // Add Document Context (Secondary)
                if (!contextChunks.isEmpty()) {
                    contextBuilder.append("Additional Context from Uploaded Documents:\n");
                    for (String chunk : contextChunks) {
                        contextBuilder.append("- ").append(chunk).append("\n");
                    }
                    contextBuilder.append("\n");
                }

                String systemPrompt = "You are a specialized Health AI Assistant for the Emergency Response app. " +
                        "Your goal is to answer patient questions using the provided medical context. " +
                        "\n\nPRIORITY RULES:" +
                        "\n1. ALWAYS prioritize 'Patient Profile Information' and 'Standardized Health Knowledge' over uploaded documents." +
                        "\n2. Use 'Additional Context from Uploaded Documents' ONLY if the answer is not found in the primary sources." +
                        "\n3. If the context contains the answer, explain it clearly but concisely." +
                        "\n4. If the context does NOT contain enough information, state: 'This information is not present in your records.' Do not guess." +
                        "\n5. Maintain a professional, empathetic, and clinical tone." +
                        "\n6. ALWAYS end your response with: 'DISCLAIMER: I am an AI assistant and this is not medical advice. Please consult a healthcare professional for clinical decisions.'";
                
                String fullPrompt = contextBuilder.toString() + "\nPatient Question: " + userQuery;

                performGroqInference(systemPrompt, fullPrompt);
            });
        }).start();
    }

    private void performGroqInference(String system, String user) {
        progressLoading.setVisibility(View.VISIBLE);
        
        List<GroqApiService.GroqRequest.Message> msgList = new ArrayList<>();
        msgList.add(new GroqApiService.GroqRequest.Message("system", system));
        msgList.add(new GroqApiService.GroqRequest.Message("user", user));

        GroqApiService.GroqRequest request = new GroqApiService.GroqRequest("llama-3.3-70b-versatile", msgList);

        groqService.getChatCompletion("Bearer " + GROQ_API_KEY, request).enqueue(new Callback<GroqApiService.GroqResponse>() {
            @Override
            public void onResponse(Call<GroqApiService.GroqResponse> call, Response<GroqApiService.GroqResponse> response) {
                if (!isAdded()) return;
                progressLoading.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    String answer = response.body().choices.get(0).message.content;
                    addMessage("AI", answer);
                } else {
                    Toast.makeText(getContext(), "Error: " + response.message(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GroqApiService.GroqResponse> call, Throwable t) {
                if (!isAdded()) return;
                progressLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Check Internet Connection", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addMessage(String sender, String content) {
        messages.add(new ChatMessage(sender, content));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
        
        saveMessageToDb(sender, content);
    }

    private void saveMessageToDb(String sender, String content) {
        String patientUuid = TokenManager.getUUID(getContext());
        new Thread(() -> {
            AppDatabase db = AppDatabaseProvider.getInstance(getContext());
            db.chatMessageDao().insertMessage(new ChatMessageEntity(patientUuid, sender, content));
        }).start();
    }

    private static class ChatMessage {
        String sender;
        String content;

        ChatMessage(String sender, String content) {
            this.sender = sender;
            this.content = content;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private List<ChatMessage> chatMessages;

        ChatAdapter(List<ChatMessage> chatMessages) {
            this.chatMessages = chatMessages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = chatMessages.get(position);
            holder.tvSender.setText(msg.sender);
            holder.tvMessage.setText(msg.content);
            
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.llContainer.getLayoutParams();
            if (msg.sender.equalsIgnoreCase("You")) {
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_user);
                holder.tvMessage.setTextColor(android.graphics.Color.WHITE);
                holder.tvSender.setVisibility(View.GONE);
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_START);
                params.removeRule(RelativeLayout.ALIGN_PARENT_END);
                holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_ai);
                holder.tvMessage.setTextColor(getResources().getColor(R.color.color_text_primary));
                holder.tvSender.setVisibility(View.VISIBLE);
            }
            holder.llContainer.setLayoutParams(params);
        }

        @Override
        public int getItemCount() {
            return chatMessages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSender, tvMessage;
            LinearLayout llContainer;

            ViewHolder(View itemView) {
                super(itemView);
                tvSender = itemView.findViewById(R.id.tv_sender);
                tvMessage = itemView.findViewById(R.id.tv_message);
                llContainer = itemView.findViewById(R.id.ll_message_container);
            }
        }
    }
}
