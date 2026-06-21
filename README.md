# Askly

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-Database-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Gemini API](https://img.shields.io/badge/Gemini%20API-AI-8E75B2?style=for-the-badge&logo=googlegemini&logoColor=white)
![Hibernate](https://img.shields.io/badge/Hibernate-ORM-59666C?style=for-the-badge&logo=hibernate&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-Frontend-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)

A document Q&A system that answers questions using **only** the content of an uploaded PDF — built with Java, Spring Boot, and Google's Gemini API, with retrieval implemented from scratch (no vector database, no LangChain).

Most RAG (Retrieval-Augmented Generation) tutorials are Python-and-LangChain projects. This one isn't — every part of the pipeline, from chunking to cosine similarity search, is hand-written in Java on top of Spring Boot, MySQL, and a direct REST integration with Gemini.

## What it does

1. Upload a PDF.
2. The backend extracts text page-by-page, splits it into overlapping chunks, and generates an embedding vector for each chunk via Gemini's embedding model.
3. Ask a question. The backend embeds the question, finds the most relevant chunks by cosine similarity, and sends only those chunks to Gemini to generate an answer.
4. The answer is returned along with the exact source passages (and page numbers) it was grounded in — so you can verify it wasn't made up.

If the document doesn't contain the answer, the system says so explicitly instead of guessing.

## Why this architecture

**Chunking, not whole-document context.** PDFs are split into ~200-word overlapping chunks rather than sent to the model whole. This keeps each request small and lets retrieval find the *specific* passage relevant to a question, rather than relying on the model to find a needle in a haystack.

**Embeddings stored in MySQL, similarity computed in Java.** Rather than reaching for a dedicated vector database (Pinecone, pgvector, etc.), embeddings are stored as serialized JSON in a `TEXT`/`LONGTEXT` column, and cosine similarity is computed directly in Java at query time. This is a deliberate scope decision: at the size of a handful of documents, a vector index adds infrastructure complexity without a real performance benefit. At scale, this is the first thing I'd swap out — see Limitations below.

**Strict grounding via prompt instruction.** The generation prompt explicitly instructs the model to answer only from the provided context and to say "I don't have enough information" otherwise. This was tested deliberately: asking an unrelated question (e.g. "what is AI" against a software engineering document) correctly returns the fallback response rather than an answer pulled from the model's general training.

**Page-aware chunking.** Text is extracted one page at a time (rather than as one flattened blob) so every chunk retains its source page number, which is surfaced in the response as a citation.

## Architecture

```
PDF upload
   │
   ▼
ChunkingService ── extracts text per page (Apache PDFBox)
   │                splits each page into overlapping word-chunks
   ▼
DocumentService ── persists Document + Chunk entities (MySQL)
   │                calls GeminiService to embed each chunk
   ▼
GeminiService ──── REST calls to Gemini's embedding + generation models
   │
   ▼
RetrievalService ── on a question: embeds the question, computes cosine
   │                 similarity against all chunks for the document,
   │                 returns the top-k matches
   ▼
QuestionController ── sends top-k chunks + question to GeminiService,
                       returns the grounded answer with citations
```

## Tech stack

| Layer | Technology |
|---|---|
| **Backend** | Java 17, Spring Boot 3, Spring Data JPA, Hibernate |
| **Database** | MySQL (chunk + embedding storage) |
| **PDF parsing** | Apache PDFBox |
| **AI — embeddings** | Gemini `gemini-embedding-001`, called directly via REST |
| **AI — generation** | Gemini `gemini-2.5-flash`, called directly via REST (no SDK) |
| **Frontend** | Vanilla HTML / CSS / JavaScript, served as Spring Boot static resources |

## Running it locally

1. Clone the repo and set up a local MySQL database named `docchat`.
2. Get a free Gemini API key from [Google AI Studio](https://aistudio.google.com) — no credit card required.
3. Set the following environment variables (in your IDE's run configuration, or your shell):
   ```
   GEMINI_API_KEY=your_key_here
   LOCAL_DB_PASSWORD=your_mysql_password
   ```
4. Update `application.properties` if your MySQL port/credentials differ from the defaults.
5. Run the app — it starts on `http://localhost:8080`, where the frontend is served directly.

## API

| Method | Endpoint | Body | Returns |
|---|---|---|---|
| `POST` | `/api/documents/upload` | multipart form, field `file` (PDF, max 10MB) | The created document with its chunks |
| `POST` | `/api/documents/ask` | `{ "documentId": 1, "question": "..." }` | Generated answer + source chunks with page numbers |

Both endpoints validate input and return clean error messages rather than stack traces for bad input — empty files, wrong file types, missing fields.

## Limitations & what I'd improve with more time

- **Similarity search is O(n) per query.** Every chunk's embedding is re-deserialized and compared on each question. Fine for a handful of documents; the first real scaling step would be a proper vector index (pgvector, or a dedicated vector DB) instead of brute-force comparison in application code.
- **Chunking is word-count based**, not sentence/paragraph-aware. It works, but can occasionally split mid-sentence. Sentence-boundary-aware chunking would improve retrieval precision.
- **No authentication.** This is intentionally out of scope for v1 — anyone with access to the running instance can upload and query documents. Adding Spring Security (JWT) is a natural next step, and one I've implemented in other projects (see [Bookstore](https://github.com/Raj0825) for a JWT-secured example).
- **Single-document Q&A only.** No cross-document search yet.
- **Free-tier API limits.** Gemini's free tier caps generation requests at 20/day on `gemini-2.5-flash` (as of mid-2026 — this number has changed multiple times and will likely change again). Fine for a portfolio demo, not production-ready without a paid tier.

## A note on what's "AI" here vs. what isn't

The content in every answer comes directly from the uploaded PDF — Gemini never "knows" the document's contents from training. Each question triggers a fresh retrieval step that hands the model only the relevant excerpt, and the model's job is narrowly reading comprehension and reformatting, not recall. This is the core idea of RAG, and it's deliberately testable: ask something the document doesn't cover, and the system says so instead of inventing an answer.
