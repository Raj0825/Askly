package com.rajshah.docchat.controller;

import com.rajshah.docchat.entity.Chunk;
import com.rajshah.docchat.service.GeminiService;
import com.rajshah.docchat.service.RetrievalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class QuestionController {

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private GeminiService geminiService;

    public static class AskRequest {
        public Long documentId;
        public String question;
    }

    public static class AskResponse {
        public String answer;
        public List<ChunkCitation> sourceChunks;

        public AskResponse(String answer, List<ChunkCitation> sourceChunks) {
            this.answer = answer;
            this.sourceChunks = sourceChunks;
        }
    }

    public static class ChunkCitation {
        public String content;
        public Integer pageNumber;

        public ChunkCitation(String content, Integer pageNumber) {
            this.content = content;
            this.pageNumber = pageNumber;
        }
    }
    @PostMapping("/api/documents/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        if (request.documentId == null) {
            return ResponseEntity.badRequest().body("documentId is required.");
        }
        if (request.question == null || request.question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("question cannot be empty.");
        }

        List<Chunk> relevantChunks = retrievalService.findTopKRelevantChunks(
                request.documentId, request.question, 3
        );

        if (relevantChunks.isEmpty()) {
            return ResponseEntity.ok(new AskResponse(
                    "No content found for this document. It may not exist or has no processed chunks.",
                    List.of()
            ));
        }

        List<ChunkCitation> chunkCitations = relevantChunks.stream()
                .map(c -> new ChunkCitation(c.getContent(), c.getPageNumber()))
                .collect(Collectors.toList());

        List<String> chunkTexts = chunkCitations.stream()
                .map(c -> c.content)
                .collect(Collectors.toList());

        String answer = geminiService.generateAnswer(request.question, chunkTexts);
        return new ResponseEntity<>(new AskResponse(answer, chunkCitations), HttpStatus.OK);
    }
}