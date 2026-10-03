package com.example.api.notes;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;

@Configuration(proxyBeanMethods = false)
class JsonInputConfiguration {
    @Bean JsonFactoryBuilderCustomizer applicationJsonReadBudget() {
        return builder -> builder.streamReadConstraints(StreamReadConstraints.builder()
                .maxDocumentLength(65536).maxStringLength(16384).maxNameLength(128)
                .maxNumberLength(64).maxNestingDepth(16).maxTokenCount(4096).build());
    }
}
