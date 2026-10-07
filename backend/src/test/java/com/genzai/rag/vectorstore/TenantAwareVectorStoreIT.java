package com.genzai.rag.vectorstore;

import com.genzai.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class TenantAwareVectorStoreIT {

    private static final String TENANT_ACME = "acme-corp";
    private static final String TENANT_GLOBEX = "globex-corp";

    // Spin up PostgreSQL with PGVector extension via Testcontainers (with container reuse enabled)
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("genzai_test_db")
     .withUsername("postgres")
     .withPassword("postgres")
     .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private VectorStore tenantAwareVectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchemaAndData() {
        TenantContext.clear();

        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector;");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vector_store (
                id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
                content TEXT,
                metadata JSONB,
                embedding VECTOR(1536)
            );
        """);

        jdbcTemplate.execute("TRUNCATE TABLE vector_store;");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Injected documents should contain tenantId in PGVector metadata table")
    void add_SavesTenantIdToPgVectorMetadata() {
        TenantContext.setTenantId(TENANT_ACME);
        Document doc = new Document("ACME Confidential Security Protocol", Map.of("classification", "TOP_SECRET"));

        tenantAwareVectorStore.add(List.of(doc));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'tenantId' = ?",
                Integer.class,
                TENANT_ACME
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("similaritySearch() should isolate results between tenants with identical document content")
    void similaritySearch_IsolatesTenantData() {
        TenantContext.setTenantId(TENANT_ACME);
        tenantAwareVectorStore.add(List.of(
                new Document("Standard operating procedure for financial refunds.", Map.of("category", "FINANCE"))
        ));

        TenantContext.setTenantId(TENANT_GLOBEX);
        tenantAwareVectorStore.add(List.of(
                new Document("Standard operating procedure for financial refunds.", Map.of("category", "FINANCE"))
        ));

        TenantContext.setTenantId(TENANT_ACME);
        List<Document> acmeResults = tenantAwareVectorStore.similaritySearch(
                SearchRequest.query("refund procedure").withTopK(10)
        );

        TenantContext.setTenantId(TENANT_GLOBEX);
        List<Document> globexResults = tenantAwareVectorStore.similaritySearch(
                SearchRequest.query("refund procedure").withTopK(10)
        );

        assertThat(acmeResults).hasSize(1);
        assertThat(acmeResults.get(0).getMetadata()).containsEntry("tenantId", TENANT_ACME);

        assertThat(globexResults).hasSize(1);
        assertThat(globexResults.get(0).getMetadata()).containsEntry("tenantId", TENANT_GLOBEX);
    }

    @Test
    @DisplayName("similaritySearch() should correctly combine tenant filter with user metadata filter")
    void similaritySearch_CombinesTenantAndMetadataFilter() {
        TenantContext.setTenantId(TENANT_ACME);
        tenantAwareVectorStore.add(List.of(
                new Document("Q3 Financial Report", Map.of("category", "FINANCE")),
                new Document("Q3 Engineering Roadmap", Map.of("category", "ENGINEERING"))
        ));

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest searchRequest = SearchRequest.query("Q3 quarterly updates")
                .withFilterExpression(b.eq("category", "FINANCE").build());

        List<Document> results = tenantAwareVectorStore.similaritySearch(searchRequest);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMetadata())
                .containsEntry("tenantId", TENANT_ACME)
                .containsEntry("category", "FINANCE");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public EmbeddingModel embeddingModel() {
            EmbeddingModel mockModel = org.mockito.Mockito.mock(EmbeddingModel.class);
            List<Double> vector = java.util.Collections.nCopies(1536, 0.1);
            org.mockito.Mockito.lenient().when(mockModel.embed(org.mockito.ArgumentMatchers.any(Document.class))).thenReturn(vector);
            org.mockito.Mockito.lenient().when(mockModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(vector);
            org.mockito.Mockito.lenient().when(mockModel.dimensions()).thenReturn(1536);
            return mockModel;
        }

        @Bean
        public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
            return new PgVectorStore(jdbcTemplate, embeddingModel);
        }

        @Bean
        public VectorStore tenantAwareVectorStore(VectorStore pgVectorStore) {
            return new TenantAwareVectorStore(pgVectorStore);
        }
    }
}
