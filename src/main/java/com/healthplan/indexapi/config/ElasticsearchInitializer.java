package com.healthplan.indexapi.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Elasticsearch Index Initializer
 *
 * Automatically creates Parent-Child Mapping on application startup
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchInitializer {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "plans";

    @PostConstruct
    public void initializeIndex() {
        try {
            // Check if index already exists
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                    .value();

            if (exists) {
                log.info("Elasticsearch index '{}' already exists, skipping creation", INDEX_NAME);
                return;
            }

            // Create Parent-Child Mapping
            log.info("Creating Elasticsearch index '{}' with Parent-Child mapping...", INDEX_NAME);

            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("objectId", Property.of(p -> p.keyword(k -> k)))
                            .properties("objectType", Property.of(p -> p.keyword(k -> k)))
                            .properties("_org", Property.of(p -> p.keyword(k -> k)))
                            .properties("planType", Property.of(p -> p.keyword(k -> k)))
                            .properties("creationDate", Property.of(p -> p.keyword(k -> k)))
                            .properties("deductible", Property.of(p -> p.integer(i -> i)))
                            .properties("copay", Property.of(p -> p.integer(i -> i)))
                            .properties("linkedService", Property.of(p -> p
                                    .object(o -> o
                                            .properties("objectId", Property.of(sp -> sp.keyword(k -> k)))
                                            .properties("name", Property.of(sp -> sp.text(t -> t)))
                                    )
                            ))
                            .properties("planserviceCostShares", Property.of(p -> p
                                    .object(o -> o
                                            .properties("deductible", Property.of(sp -> sp.integer(i -> i)))
                                            .properties("copay", Property.of(sp -> sp.integer(i -> i)))
                                    )
                            ))
                            .properties("plan_join", Property.of(p -> p
                                    .join(j -> j
                                            .relations(Map.of(
                                                    "plan", java.util.List.of("planCostShares", "linkedPlanService")
                                            ))
                                    )
                            ))
                    )
            ));

            log.info("Successfully created Elasticsearch index '{}' with Parent-Child mapping", INDEX_NAME);

        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch index: {}", e.getMessage(), e);
            // Don't throw, let application continue
        }
    }
}
