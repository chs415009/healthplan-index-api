package com.healthplan.indexapi.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch Indexing Service
 * Responsible for decomposing Plan JSON and indexing to Elasticsearch
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;

    /**
     * Index Plan to Elasticsearch (create Parent-Child relationship)
     */
    public void indexPlan(String objectId, String jsonData) {
        try {
            JsonNode planNode = objectMapper.readTree(jsonData);

            // Index main Plan document (parent document)
            indexPlanDocument(planNode);

            // Index PlanCostShares (child document)
            if (planNode.has("planCostShares")) {
                indexPlanCostShares(planNode.get("planCostShares"), objectId);
            }

            // Index LinkedPlanServices (child documents)
            if (planNode.has("linkedPlanServices")) {
                JsonNode services = planNode.get("linkedPlanServices");
                if (services.isArray()) {
                    for (JsonNode service : services) {
                        indexLinkedPlanService(service, objectId);
                    }
                }
            }

            log.info("Successfully indexed Plan to Elasticsearch: objectId={}", objectId);

        } catch (Exception e) {
            log.error("Failed to index Plan to Elasticsearch: objectId={}, error={}",
                    objectId, e.getMessage(), e);
            throw new RuntimeException("Elasticsearch indexing failed", e);
        }
    }

    /**
     * Delete Plan and all related documents from Elasticsearch
     * Strategy: Use delete_by_query to delete all child documents, then delete parent
     */
    public void deletePlan(String objectId) {
        try {
            // First delete all child documents (using has_parent query)
            elasticsearchClient.deleteByQuery(d -> d
                    .index("plans")
                    .query(q -> q
                            .hasParent(hp -> hp
                                    .parentType("plan")
                                    .query(pq -> pq
                                            .term(t -> t
                                                    .field("objectId")
                                                    .value(objectId)
                                            )
                                    )
                            )
                    )
            );
            log.debug("Deleted child documents for Plan: {}", objectId);

            // Delete parent document (Plan itself)
            elasticsearchClient.delete(del -> del
                    .index("plans")
                    .id(objectId)
            );
            log.debug("Deleted Plan document: {}", objectId);
            log.info("Successfully deleted Plan from Elasticsearch: objectId={}", objectId);

        } catch (Exception e) {
            log.error("Failed to delete Plan from Elasticsearch: objectId={}, error={}",
                    objectId, e.getMessage(), e);
            // Don't throw exception since MongoDB is already deleted, this is just a sync issue
        }
    }

    // =========================================================================
    // Helper methods for the public methods
    // =========================================================================

    /**
     * Index main Plan document
     */
    private void indexPlanDocument(JsonNode planNode) throws Exception {
        String objectId = planNode.get("objectId").asText();

        // Create Plan document data
        Map<String, Object> planDoc = new HashMap<>();
        planDoc.put("objectId", objectId);
        planDoc.put("objectType", planNode.get("objectType").asText());
        planDoc.put("_org", planNode.get("_org").asText());
        planDoc.put("planType", planNode.get("planType").asText());
        planDoc.put("creationDate", planNode.get("creationDate").asText());
        // Important: Parent document also needs plan_join field!
        planDoc.put("plan_join", Map.of("name", "plan"));

        // Index to Elasticsearch
        IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index("plans")
                .id(objectId)
                .document(planDoc)
        );

        elasticsearchClient.index(request);
        log.debug("Indexed Plan document: {}", objectId);
    }

    /**
     * Index PlanCostShares child document
     */
    private void indexPlanCostShares(JsonNode costShareNode, String parentId) throws Exception {
        String objectId = costShareNode.get("objectId").asText();

        Map<String, Object> doc = new HashMap<>();
        doc.put("objectId", objectId);
        doc.put("objectType", costShareNode.get("objectType").asText());
        doc.put("_org", costShareNode.get("_org").asText());
        doc.put("deductible", costShareNode.get("deductible").asInt());
        doc.put("copay", costShareNode.get("copay").asInt());
        doc.put("plan_join", Map.of("name", "planCostShares", "parent", parentId));

        IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index("plans")
                .id(objectId)
                .routing(parentId)  // Important: use parent ID as routing
                .document(doc)
        );

        elasticsearchClient.index(request);
        log.debug("Indexed PlanCostShares: {} (parent: {})", objectId, parentId);
    }

    /**
     * Index LinkedPlanService child document
     */
    private void indexLinkedPlanService(JsonNode serviceNode, String parentId) throws Exception {
        String objectId = serviceNode.get("objectId").asText();

        Map<String, Object> doc = new HashMap<>();
        doc.put("objectId", objectId);
        doc.put("objectType", serviceNode.get("objectType").asText());
        doc.put("_org", serviceNode.get("_org").asText());
        doc.put("plan_join", Map.of("name", "linkedPlanService", "parent", parentId));

        // Add linkedService info
        if (serviceNode.has("linkedService")) {
            JsonNode linkedService = serviceNode.get("linkedService");
            doc.put("linkedService", Map.of(
                    "objectId", linkedService.get("objectId").asText(),
                    "name", linkedService.get("name").asText()
            ));
        }

        // Add planserviceCostShares info
        if (serviceNode.has("planserviceCostShares")) {
            JsonNode costShares = serviceNode.get("planserviceCostShares");
            doc.put("planserviceCostShares", Map.of(
                    "deductible", costShares.get("deductible").asInt(),
                    "copay", costShares.get("copay").asInt()
            ));
        }

        IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index("plans")
                .id(objectId)
                .routing(parentId)  // Important: use parent ID as routing
                .document(doc)
        );

        elasticsearchClient.index(request);
        log.debug("Indexed LinkedPlanService: {} (parent: {})", objectId, parentId);
    }
}
