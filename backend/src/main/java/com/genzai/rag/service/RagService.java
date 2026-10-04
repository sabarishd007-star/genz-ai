package com.genzai.rag.service;

import com.genzai.rag.dto.DocumentResponse;
import com.genzai.rag.entity.RagDocument;
import com.genzai.rag.repository.RagDocumentRepository;
import com.genzai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagDocumentRepository ragDocumentRepository;

    @Transactional
    public DocumentResponse ingestDocument(MultipartFile file, UserPrincipal currentUser) throws IOException {
        String docId = UUID.randomUUID().toString();
        String userId = (currentUser != null && currentUser.getId() != null)
                ? currentUser.getId().toString()
                : "anonymous";

        // Save initial "processing" record
        RagDocument ragDoc = RagDocument.builder()
                .id(docId)
                .name(file.getOriginalFilename())
                .size(file.getSize())
                .status("processing")
                .userId(userId)
                .build();
        ragDocumentRepository.save(ragDoc);

        try {
            // Read PDF using Spring AI's PDF reader from in-memory bytes
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();

            // Chunk documents
            TokenTextSplitter splitter = new TokenTextSplitter(800, 350, 5, 10000, true);
            List<Document> chunks = splitter.apply(documents);

            // Attach metadata
            chunks.forEach(doc -> {
                Map<String, Object> meta = new HashMap<>(doc.getMetadata());
                meta.put("documentId", docId);
                meta.put("fileName", file.getOriginalFilename());
                meta.put("userId", userId);
                doc.getMetadata().putAll(meta);
            });

            // Store embeddings in PGVector
            vectorStore.accept(chunks);

            // Update record to "indexed"
            ragDoc.setStatus("indexed");
            ragDoc.setChunkCount(chunks.size());
            ragDocumentRepository.save(ragDoc);

            log.info("Document '{}' ingested: {} chunks, docId={}", file.getOriginalFilename(), chunks.size(), docId);

        } catch (Exception e) {
            log.error("Document ingestion failed for docId={}: {}", docId, e.getMessage());
            ragDoc.setStatus("failed");
            ragDocumentRepository.save(ragDoc);
        }

        return toResponse(ragDoc);
    }

    public List<DocumentResponse> getDocumentsForUser(UserPrincipal currentUser) {
        String userId = (currentUser != null && currentUser.getId() != null)
                ? currentUser.getId().toString()
                : "anonymous";
        return ragDocumentRepository
                .findByUserIdOrderByUploadedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDocument(String documentId, UserPrincipal currentUser) {
        String userId = (currentUser != null && currentUser.getId() != null)
                ? currentUser.getId().toString()
                : "anonymous";
        ragDocumentRepository.deleteByIdAndUserId(documentId, userId);
        log.info("Deleted document id={} for userId={}", documentId, userId);
    }

    public Flux<String> streamChatWithRag(String query, List<String> documentIds, UserPrincipal currentUser) {
        SearchRequest request = SearchRequest.query(query).withTopK(5);

        if (documentIds != null && !documentIds.isEmpty()) {
            request = request.withFilterExpression(
                    "documentId in ['" + String.join("','", documentIds) + "']"
            );
        }

        List<Document> relevant = vectorStore.similaritySearch(request);

        if (relevant.isEmpty()) {
            return Flux.just("I couldn't find any relevant information in your uploaded documents. Please make sure you've selected documents that contain relevant content.");
        }

        String context = relevant.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = """
                You are an intelligent academic assistant. Answer questions using ONLY the provided context from the user's documents.
                Be concise, accurate and cite the source when possible.
                If the answer cannot be found in the context, say so clearly.
                """;

        String userPrompt = """
                CONTEXT FROM DOCUMENTS:
                %s
                
                QUESTION: %s
                """.formatted(context, query);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content();
    }

    private DocumentResponse toResponse(RagDocument doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .name(doc.getName())
                .size(doc.getSize())
                .status(doc.getStatus())
                .chunkCount(doc.getChunkCount())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }
}
