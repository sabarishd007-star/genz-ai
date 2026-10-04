package com.genzai.rag.controller;

import com.genzai.rag.dto.ChatRequest;
import com.genzai.rag.dto.DocumentResponse;
import com.genzai.rag.service.RagService;
import com.genzai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/documents/upload")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser) throws IOException {
        DocumentResponse response = ragService.ingestDocument(file, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents")
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ragService.getDocumentsForUser(currentUser));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("id") String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ragService.deleteDocument(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ragService.streamChatWithRag(request.query(), request.documentIds(), currentUser);
    }
}
