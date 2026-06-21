package com.rajshah.docchat.service;

import com.rajshah.docchat.entity.Chunk;
import com.rajshah.docchat.entity.Document;
import com.rajshah.docchat.repository.ChunkRepository;
import com.rajshah.docchat.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentService {

    @Autowired
    private ChunkingService chunkingService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    public Document uploadAndProcess(MultipartFile file) throws IOException {
        List<ChunkingService.PageChunk> pageChunks = chunkingService.extractAndChunkByPage(file);

        Document document = new Document();
        document.setFileName(file.getOriginalFilename());
        documentRepository.save(document);

        int chunkIndex = 0;
        for (ChunkingService.PageChunk pageChunk : pageChunks) {
            float[] embedding = geminiService.getEmbedding(pageChunk.content);
            String embeddingJson = geminiService.embeddingToJson(embedding);

            Chunk chunk = new Chunk();
            chunk.setContent(pageChunk.content);
            chunk.setChunkIndex(chunkIndex++);
            chunk.setPageNumber(pageChunk.pageNumber);
            chunk.setEmbeddingJson(embeddingJson);
            chunk.setDocument(document);
            chunkRepository.save(chunk);
        }

        List<Chunk> savedChunks = chunkRepository.findByDocumentId(document.getId());
        document.setChunks(savedChunks);

        return document;
    }
}