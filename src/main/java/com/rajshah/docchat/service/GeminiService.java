package com.rajshah.docchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.embedding-url}")
    private String embeddingUrl;

    @Value("${gemini.api.generate-url}")
    private String generateUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public float[] getEmbedding(String text) {
        Map<String, Object> requestBody = Map.of(
                "model", "models/gemini-embedding-001",
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = embeddingUrl + "?key=" + apiKey;
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode valuesNode = root.path("embedding").path("values");

            float[] result = new float[valuesNode.size()];
            for (int i = 0; i < valuesNode.size(); i++) {
                result[i] = (float) valuesNode.get(i).asDouble();
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini embedding response: " + response.getBody(), e);
        }
    }

    public String generateAnswer(String question, List<String> contextChunks) {
        String context = String.join("\n\n---\n\n", contextChunks);

        String prompt = """
        Answer the question using ONLY the context below.
        If the context doesn't contain the answer, say "I don't have enough information in this document to answer that."

        Context:
        %s

        Question: %s
        """.formatted(context, question);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = generateUrl + "?key=" + apiKey;

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            return "I'm currently rate-limited by the AI provider (free tier quota reached). Please try again in a minute.";
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini generate response", e);
        }
    }

    public String embeddingToJson(float[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize embedding", e);
        }
    }

    public float[] jsonToEmbedding(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize embedding", e);
        }
    }
}