package com.genzai.rag.vectorstore;

import com.genzai.tenant.TenantContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Optional;

public class TenantAwareVectorStore implements VectorStore {

    private final VectorStore delegate;

    public TenantAwareVectorStore(VectorStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public void add(List<Document> documents) {
        String tenantId = getRequiredTenantId();
        for (Document doc : documents) {
            doc.getMetadata().put("tenantId", tenantId);
        }
        delegate.add(documents);
    }

    @Override
    public void accept(List<Document> documents) {
        add(documents);
    }

    @Override
    public Optional<Boolean> delete(List<String> idList) {
        return delegate.delete(idList);
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(SearchRequest.query(query));
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String tenantId = getRequiredTenantId();
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        Filter.Expression tenantFilter = builder.eq("tenantId", tenantId).build();

        Filter.Expression combinedFilter;
        if (request.hasFilterExpression()) {
            combinedFilter = new Filter.Expression(
                    Filter.ExpressionType.AND,
                    tenantFilter,
                    request.getFilterExpression()
            );
        } else {
            combinedFilter = tenantFilter;
        }

        SearchRequest tenantScopedRequest = SearchRequest.from(request)
                .withFilterExpression(combinedFilter);

        return delegate.similaritySearch(tenantScopedRequest);
    }

    private String getRequiredTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is missing! Cannot execute vector store operations without a valid tenantId.");
        }
        return tenantId;
    }
}
