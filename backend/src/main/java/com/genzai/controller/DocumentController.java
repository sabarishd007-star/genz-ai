package com.genzai.controller;

import com.genzai.dto.DocumentChunkDto;
import com.genzai.dto.DocumentResponseDto;
import com.genzai.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping({"/api/documents", "/documents"})
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponseDto>> getDocuments(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(documentService.getUserDocuments(userId, subject, query));
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<List<DocumentChunkDto>> getChunks(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentChunks(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        documentService.deleteDocument(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentResponseDto> uploadDocument(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "subject", defaultValue = "General") String subject) {
        return ResponseEntity.ok(documentService.uploadDocument(userId, file, subject));
    }
}
