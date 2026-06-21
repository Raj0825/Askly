package com.rajshah.docchat.service;

import com.rajshah.docchat.entity.Chunk;
import com.rajshah.docchat.repository.ChunkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RetrievalService {

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private GeminiService geminiService;

    public double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public List<Chunk> findTopKRelevantChunks(Long documentId, String question, int k) {
        float[] questionEmbedding = geminiService.getEmbedding(question);
        List<Chunk> allChunks = chunkRepository.findByDocumentId(documentId);

        return allChunks.stream()
                .filter(chunk -> chunk.getEmbeddingJson() != null)
                .sorted(Comparator.comparingDouble(chunk -> {
                    float[] chunkEmbedding = geminiService.jsonToEmbedding(chunk.getEmbeddingJson());
                    return -cosineSimilarity(questionEmbedding, chunkEmbedding);
                }))
                .limit(k)
                .collect(Collectors.toList());
    }
}