package com.rajshah.docchat.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 200; // words per chunk
    private static final int OVERLAP_SIZE = 30; // words of overlap between chunks

    public static class PageChunk {
        public String content;
        public int pageNumber;

        public PageChunk(String content, int pageNumber) {
            this.content = content;
            this.pageNumber = pageNumber;
        }
    }

    public List<PageChunk> extractAndChunkByPage(MultipartFile file) throws IOException {
        List<PageChunk> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {

            int totalPages = document.getNumberOfPages();

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);

                String pageText = stripper.getText(document);
                if (pageText == null || pageText.trim().isEmpty()) {
                    continue; // skip blank pages
                }

                List<String> chunksForPage = splitIntoChunks(pageText);
                for (String chunkText : chunksForPage) {
                    result.add(new PageChunk(chunkText, pageIndex + 1)); // 1-indexed page numbers
                }
            }
        }

        return result;
    }

    public List<String> splitIntoChunks(String fullText) {
        List<String> chunks = new ArrayList<>();
        String[] words = fullText.split("\\s+");

        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + CHUNK_SIZE, words.length);
            StringBuilder chunk = new StringBuilder();
            for (int i = start; i < end; i++) {
                chunk.append(words[i]).append(" ");
            }
            chunks.add(chunk.toString().trim());

            if (end == words.length) break;
            start = end - OVERLAP_SIZE;
        }
        return chunks;
    }
}