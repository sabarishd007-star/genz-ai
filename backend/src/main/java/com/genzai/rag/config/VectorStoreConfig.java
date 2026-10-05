package com.genzai.rag.config;

import com.genzai.rag.vectorstore.TenantAwareVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public static BeanPostProcessor tenantAwareVectorStorePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof VectorStore vectorStore && !(bean instanceof TenantAwareVectorStore)) {
                    return new TenantAwareVectorStore(vectorStore);
                }
                return bean;
            }
        };
    }
}
