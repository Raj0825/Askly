package com.rajshah.docchat.repository;

import com.rajshah.docchat.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    List<Chunk> findByDocumentId(Long documentId);


}