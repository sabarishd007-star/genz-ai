package com.genzai.service;

import com.genzai.model.Document;
import com.genzai.model.User;
import com.genzai.repository.DocumentChunkRepository;
import com.genzai.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, chunkRepository);
    }

    @Test
    void preventAccessToOtherUserDocumentChunks() {
        Long ownerId = 100L;
        Long attackerUserId = 999L;
        Long docId = 42L;

        when(documentRepository.findByIdAndUserId(docId, attackerUserId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> 
            documentService.getDocumentChunks(attackerUserId, docId)
        );

        verify(chunkRepository, never()).findByDocumentIdOrderByChunkIndexAsc(anyLong());
    }
}
