package com.rajshah.docchat.controller;

import com.rajshah.docchat.entity.Document;
import com.rajshah.docchat.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/api/documents/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty. Please upload a valid PDF.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body("Only PDF files are supported.");
        }

        if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
            return ResponseEntity.badRequest().body("File too large. Maximum size is 10MB.");
        }

        Document document = documentService.uploadAndProcess(file);
        return ResponseEntity.ok(document);
    }
}