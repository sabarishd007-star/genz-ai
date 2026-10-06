package com.genzai.rag.vectorstore;

import com.genzai.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAwareVectorStoreTest {

    private static final String TEST_TENANT_ID = "tenant-acme-123";

    @Mock
    private VectorStore delegateVectorStore;

    @InjectMocks
    private TenantAwareVectorStore tenantAwareVectorStore;

    @Captor
    private ArgumentCaptor<List<Document>> documentsCaptor;

    @Captor
    private ArgumentCaptor<SearchRequest> searchRequestCaptor;

    @BeforeEach
    @AfterEach
    void resetTenantContext() {
        TenantContext.clear();
    }

    // ==========================================
    // 1. INGESTION TESTS (add)
    // ==========================================

    @Test
    @DisplayName("add() should inject current tenantId into metadata for all documents")
    void add_InjectsTenantIdIntoDocumentMetadata() {
        // Given
        TenantContext.setTenantId(TEST_TENANT_ID);

        Document doc1 = new Document("Content 1", new HashMap<>(Map.of("author", "Alice")));
        Document doc2 = new Document("Content 2", new HashMap<>(Map.of("author", "Bob")));
        List<Document> documents = List.of(doc1, doc2);

        // When
        tenantAwareVectorStore.add(documents);

        // Then
        verify(delegateVectorStore, times(1)).add(documentsCaptor.capture());
        List<Document> capturedDocs = documentsCaptor.getValue();

        assertThat(capturedDocs).hasSize(2);
        assertThat(capturedDocs.get(0).getMetadata())
                .containsEntry("tenantId", TEST_TENANT_ID)
                .containsEntry("author", "Alice");
        assertThat(capturedDocs.get(1).getMetadata())
                .containsEntry("tenantId", TEST_TENANT_ID)
                .containsEntry("author", "Bob");
    }

    @Test
    @DisplayName("add() should throw IllegalStateException when TenantContext is missing")
    void add_ThrowsException_WhenTenantContextMissing() {
        // Given - No tenant set in TenantContext
        Document doc = new Document("Sample content");

        // When / Then
        assertThatThrownBy(() -> tenantAwareVectorStore.add(List.of(doc)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context is missing");

        verify(delegateVectorStore, never()).add(any());
    }

    // ==========================================
    // 2. SEARCH TESTS (similaritySearch)
    // ==========================================

    @Test
    @DisplayName("similaritySearch() should inject tenant filter when no user filter exists")
    void similaritySearch_InjectsTenantFilter_WhenNoUserFilter() {
        // Given
        TenantContext.setTenantId(TEST_TENANT_ID);
        SearchRequest request = SearchRequest.query("What is the refund policy?");

        Filter.Expression expectedFilter = new FilterExpressionBuilder()
                .eq("tenantId", TEST_TENANT_ID)
                .build();

        when(delegateVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Match result")));

        // When
        List<Document> results = tenantAwareVectorStore.similaritySearch(request);

        // Then
        assertThat(results).hasSize(1);
        verify(delegateVectorStore).similaritySearch(searchRequestCaptor.capture());

        SearchRequest executedRequest = searchRequestCaptor.getValue();
        assertThat(executedRequest.getQuery()).isEqualTo("What is the refund policy?");
        assertThat(executedRequest.getFilterExpression()).isEqualTo(expectedFilter);
    }

    @Test
    @DisplayName("similaritySearch() should combine tenant filter with existing user filter using AND")
    void similaritySearch_CombinesTenantAndUserFilters() {
        // Given
        TenantContext.setTenantId(TEST_TENANT_ID);

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression userFilter = builder.eq("category", "FINANCE").build();

        SearchRequest request = SearchRequest.query("Q3 Financials")
                .withFilterExpression(userFilter);

        Filter.Expression expectedCombinedFilter = builder.and(
                builder.eq("tenantId", TEST_TENANT_ID).build(),
                userFilter
        ).build();

        when(delegateVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        // When
        tenantAwareVectorStore.similaritySearch(request);

        // Then
        verify(delegateVectorStore).similaritySearch(searchRequestCaptor.capture());

        SearchRequest executedRequest = searchRequestCaptor.getValue();
        assertThat(executedRequest.getFilterExpression()).isEqualTo(expectedCombinedFilter);
    }

    @Test
    @DisplayName("similaritySearch() string variant should delegate to SearchRequest overload with tenant filter")
    void similaritySearch_StringQuery_InjectsTenantFilter() {
        // Given
        TenantContext.setTenantId(TEST_TENANT_ID);
        Filter.Expression expectedFilter = new FilterExpressionBuilder()
                .eq("tenantId", TEST_TENANT_ID)
                .build();

        when(delegateVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        // When
        tenantAwareVectorStore.similaritySearch("Find technical docs");

        // Then
        verify(delegateVectorStore).similaritySearch(searchRequestCaptor.capture());
        SearchRequest executedRequest = searchRequestCaptor.getValue();

        assertThat(executedRequest.getQuery()).isEqualTo("Find technical docs");
        assertThat(executedRequest.getFilterExpression()).isEqualTo(expectedFilter);
    }

    @Test
    @DisplayName("similaritySearch() should throw IllegalStateException when TenantContext is missing")
    void similaritySearch_ThrowsException_WhenTenantContextMissing() {
        // Given - No tenant set in TenantContext
        SearchRequest request = SearchRequest.query("Unauthorized search");

        // When / Then
        assertThatThrownBy(() -> tenantAwareVectorStore.similaritySearch(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context is missing");

        verify(delegateVectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    // ==========================================
    // 3. DELEGATION TESTS (delete)
    // ==========================================

    @Test
    @DisplayName("delete() should delegate directly to underlying VectorStore")
    void delete_DelegatesToUnderlyingVectorStore() {
        // Given
        List<String> ids = List.of("id-1", "id-2");

        // When
        tenantAwareVectorStore.delete(ids);

        // Then
        verify(delegateVectorStore, times(1)).delete(ids);
    }
}
