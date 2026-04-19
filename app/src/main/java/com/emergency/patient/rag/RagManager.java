package com.emergency.patient.rag;

import android.content.Context;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentChunkEntity;
import com.emergency.patient.db.HealthDocumentEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RagManager {
    private static final int CHUNK_SIZE = 600; 
    private static final int CHUNK_OVERLAP = 150;

    /**
     * Chunks the document text and stores it in the database.
     * Preserves sentence boundaries where possible.
     */
    public static void chunkAndStore(Context context, HealthDocumentEntity doc) {
        if (doc.fullText == null || doc.fullText.isEmpty()) return;

        List<HealthDocumentChunkEntity> chunks = new ArrayList<>();
        String text = doc.fullText.replaceAll("\\s+", " "); // Normalize whitespace
        
        int start = 0;
        int index = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            
            // Try to find a sentence end (., !, ?) within the last 100 characters of the chunk
            if (end < text.length()) {
                int lastSentenceEnd = -1;
                String suffix = text.substring(Math.max(start, end - 100), end);
                int dot = suffix.lastIndexOf('.');
                int exclamation = suffix.lastIndexOf('!');
                int question = suffix.lastIndexOf('?');
                
                lastSentenceEnd = Math.max(dot, Math.max(exclamation, question));
                if (lastSentenceEnd != -1) {
                    end = Math.max(start, end - 100) + lastSentenceEnd + 1;
                }
            }

            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new HealthDocumentChunkEntity(doc.id, doc.patientUuid, content, index++));
            }
            
            if (end >= text.length()) break;
            start = end - CHUNK_OVERLAP; // Ensure overlap for context continuity
        }

        AppDatabaseProvider.getInstance(context).healthDocumentChunkDao().insertChunks(chunks);
    }

    /**
     * Retrieves the top relevant chunks using weighted keyword matching.
     */
    public static List<String> getRelevantContext(Context context, String patientUuid, String query) {
        List<HealthDocumentChunkEntity> allChunks = AppDatabaseProvider.getInstance(context)
                .healthDocumentChunkDao().getChunksForPatient(patientUuid);
        
        if (allChunks.isEmpty()) return new ArrayList<>();

        // 1. Prepare query keywords with basic stop-word filtering
        List<String> queryKeywords = new ArrayList<>();
        String[] rawWords = query.toLowerCase().split("[^a-zA-Z0-9]+");
        List<String> stopWords = java.util.Arrays.asList("is", "the", "and", "a", "an", "to", "of", "in", "it", "my", "me", "how", "what", "can", "tell");
        
        for (String word : rawWords) {
            if (word.length() > 2 && !stopWords.contains(word)) {
                queryKeywords.add(word);
            }
        }
        
        if (queryKeywords.isEmpty()) {
            // Fallback to all words if filtering removed everything
            Collections.addAll(queryKeywords, rawWords);
        }

        List<ChunkScore> scores = new ArrayList<>();

        for (HealthDocumentChunkEntity chunk : allChunks) {
            int score = 0;
            String content = chunk.content.toLowerCase();
            
            for (String kw : queryKeywords) {
                // Heuristic: exact word matches score higher than partial matches
                if (content.contains(" " + kw + " ") || content.startsWith(kw + " ") || content.endsWith(" " + kw)) {
                    score += 10;
                } else if (content.contains(kw)) {
                    score += 2;
                }
            }
            
            if (score > 0) {
                scores.add(new ChunkScore(chunk.content, score));
            }
        }

        Collections.sort(scores, (a, b) -> b.score - a.score);
        
        List<String> contextList = new ArrayList<>();
        // Return top 4 chunks for better LLM context density
        for (int i = 0; i < Math.min(4, scores.size()); i++) {
            contextList.add(scores.get(i).content);
        }
        return contextList;
    }

    private static class ChunkScore {
        String content;
        int score;

        ChunkScore(String content, int score) {
            this.content = content;
            this.score = score;
        }
    }
}
