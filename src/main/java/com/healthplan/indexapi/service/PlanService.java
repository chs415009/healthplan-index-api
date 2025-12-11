package com.healthplan.indexapi.service;

import com.healthplan.indexapi.exception.ResourceAlreadyExistsException;
import com.healthplan.indexapi.exception.ResourceNotFoundException;
import com.healthplan.indexapi.model.PlanEntity;
import com.healthplan.indexapi.repository.PlanRepository;
import com.healthplan.indexapi.util.ETagGenerator;
import com.healthplan.indexapi.util.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository repository;
    private final JsonSchemaValidator validator;
    private final ObjectMapper objectMapper;
    private final QueueService queueService;

    public String createPlan(String jsonString) {
        validator.validate(jsonString);

        try {
            JsonNode planNode = objectMapper.readTree(jsonString);
            String planId = planNode.get("objectId").asText();

            if (repository.existsById(planId)) throw new ResourceAlreadyExistsException(planId);

            savePlanDocuments(planNode); // Decompose and save to MongoDB as separate documents
            log.info("Plan saved to MongoDB: objectId={}", planId);

            queueService.sendIndexMessage(planId, jsonString);
            return planId;

        } catch (ResourceAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create plan: " + e.getMessage(), e);
        }
    }

    public String getPlan(String objectId) {
        try {
            if (!repository.existsById(objectId)) throw new ResourceNotFoundException(objectId);

            return reconstructPlanJson(objectId); // Reconstruct complete JSON from decomposed documents

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to get plan: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deletePlan(String objectId) {
        if (!repository.existsById(objectId)) throw new ResourceNotFoundException(objectId);

        try {
            deletePlanDocuments(objectId); // Delete all related documents recursively
            log.info("Plan deleted from MongoDB: objectId={}", objectId);

            queueService.sendDeleteMessage(objectId);

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to delete plan: " + e.getMessage(), e);
        }
    }

    @Transactional
    public String patchPlan(String objectId, String patchBody) {
        String existingJson = getPlan(objectId);

        try {
            JsonNode existingNode = objectMapper.readTree(existingJson);
            JsonNode patchNode = objectMapper.readTree(patchBody);
            JsonNode mergedNode = deepMerge(existingNode, patchNode);
            String mergedJson = objectMapper.writeValueAsString(mergedNode);

            validator.validate(mergedJson);

            // Delete old documents and save new ones
            deletePlanDocuments(objectId);
            savePlanDocuments(objectMapper.readTree(mergedJson));

            log.info("Plan updated in MongoDB: objectId={}", objectId);

            queueService.sendUpdateMessage(objectId, mergedJson);
            return mergedJson;

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to patch plan: " + e.getMessage(), e);
        }
    }

    public String generateETag(String jsonString) {
        return ETagGenerator.generate(jsonString);
    }

    // =========================================================================
    // Helper methods for the public methods
    // =========================================================================

    /**
     * Decompose Plan JSON and save as separate MongoDB documents
     */
    private void savePlanDocuments(JsonNode planNode) {
        String planId = planNode.get("objectId").asText();

        // 1. Save main Plan document
        Map<String, Object> planAttrs = new HashMap<>();
        planAttrs.put("_org", planNode.get("_org").asText());
        planAttrs.put("planType", planNode.get("planType").asText());
        planAttrs.put("creationDate", planNode.get("creationDate").asText());

        PlanEntity planEntity = PlanEntity.builder()
                .id(planId)
                .objectType("plan")
                .parentId(null)
                .attributes(planAttrs)
                .build();
        repository.save(planEntity);
        log.debug("Saved Plan document: {}", planId);

        // 2. Save PlanCostShares
        if (planNode.has("planCostShares")) {
            JsonNode costShare = planNode.get("planCostShares");
            String costShareId = costShare.get("objectId").asText();

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("_org", costShare.get("_org").asText());
            attrs.put("deductible", costShare.get("deductible").asInt());
            attrs.put("copay", costShare.get("copay").asInt());

            PlanEntity entity = PlanEntity.builder()
                    .id(costShareId)
                    .objectType("membercostshare")
                    .parentId(planId)
                    .attributes(attrs)
                    .build();
            repository.save(entity);
            log.debug("Saved PlanCostShares: {}", costShareId);
        }

        // 3. Save LinkedPlanServices and nested objects
        if (planNode.has("linkedPlanServices")) {
            JsonNode servicesArray = planNode.get("linkedPlanServices");
            for (JsonNode ps : servicesArray) {
                String psId = ps.get("objectId").asText();

                // 3.1 Save LinkedPlanService
                Map<String, Object> psAttrs = new HashMap<>();
                psAttrs.put("_org", ps.get("_org").asText());

                PlanEntity psEntity = PlanEntity.builder()
                        .id(psId)
                        .objectType("planservice")
                        .parentId(planId)
                        .attributes(psAttrs)
                        .build();
                repository.save(psEntity);
                log.debug("Saved LinkedPlanService: {}", psId);

                // 3.2 Save LinkedService
                if (ps.has("linkedService")) {
                    JsonNode service = ps.get("linkedService");
                    String serviceId = service.get("objectId").asText();

                    Map<String, Object> sAttrs = new HashMap<>();
                    sAttrs.put("_org", service.get("_org").asText());
                    sAttrs.put("name", service.get("name").asText());

                    PlanEntity sEntity = PlanEntity.builder()
                            .id(serviceId)
                            .objectType("service")
                            .parentId(psId)
                            .attributes(sAttrs)
                            .build();
                    repository.save(sEntity);
                    log.debug("Saved Service: {}", serviceId);
                }

                // 3.3 Save PlanServiceCostShares
                if (ps.has("planserviceCostShares")) {
                    JsonNode psCostShare = ps.get("planserviceCostShares");
                    String pscsId = psCostShare.get("objectId").asText();

                    Map<String, Object> pscsAttrs = new HashMap<>();
                    pscsAttrs.put("_org", psCostShare.get("_org").asText());
                    pscsAttrs.put("deductible", psCostShare.get("deductible").asInt());
                    pscsAttrs.put("copay", psCostShare.get("copay").asInt());

                    PlanEntity pscsEntity = PlanEntity.builder()
                            .id(pscsId)
                            .objectType("membercostshare")
                            .parentId(psId)
                            .attributes(pscsAttrs)
                            .build();
                    repository.save(pscsEntity);
                    log.debug("Saved PlanServiceCostShares: {}", pscsId);
                }
            }
        }
    }

    /**
     * Reconstruct complete Plan JSON from decomposed MongoDB documents
     */
    private String reconstructPlanJson(String planId) throws Exception {
        // 1. Get main Plan document
        PlanEntity planEntity = repository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException(planId));

        // 2. Build Plan JSON
        Map<String, Object> planJson = new HashMap<>();
        planJson.put("objectId", planId);
        planJson.put("objectType", "plan");
        planJson.putAll(planEntity.getAttributes());

        // 3. Get and add PlanCostShares
        List<PlanEntity> directChildren = repository.findByParentId(planId);
        for (PlanEntity child : directChildren) {
            if ("membercostshare".equals(child.getObjectType())) {
                // This is PlanCostShares
                Map<String, Object> costShare = new HashMap<>();
                costShare.put("objectId", child.getId());
                costShare.put("objectType", child.getObjectType());
                costShare.putAll(child.getAttributes());
                planJson.put("planCostShares", costShare);

            } else if ("planservice".equals(child.getObjectType())) {
                // This is LinkedPlanService - need to get its children
                if (!planJson.containsKey("linkedPlanServices")) {
                    planJson.put("linkedPlanServices", new ArrayList<>());
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> services = (List<Map<String, Object>>) planJson.get("linkedPlanServices");

                Map<String, Object> psJson = new HashMap<>();
                psJson.put("objectId", child.getId());
                psJson.put("objectType", child.getObjectType());
                psJson.putAll(child.getAttributes());

                // Get children of this PlanService
                List<PlanEntity> psChildren = repository.findByParentId(child.getId());
                for (PlanEntity psChild : psChildren) {
                    if ("service".equals(psChild.getObjectType())) {
                        Map<String, Object> service = new HashMap<>();
                        service.put("objectId", psChild.getId());
                        service.put("objectType", psChild.getObjectType());
                        service.putAll(psChild.getAttributes());
                        psJson.put("linkedService", service);

                    } else if ("membercostshare".equals(psChild.getObjectType())) {
                        Map<String, Object> costShare = new HashMap<>();
                        costShare.put("objectId", psChild.getId());
                        costShare.put("objectType", psChild.getObjectType());
                        costShare.putAll(psChild.getAttributes());
                        psJson.put("planserviceCostShares", costShare);
                    }
                }
                services.add(psJson);
            }
        }
        return objectMapper.writeValueAsString(planJson);
    }

    /**
     * Delete Plan and all related documents recursively
     */
    private void deletePlanDocuments(String planId) {
        List<PlanEntity> children = repository.findByParentId(planId); // Get all direct children

        // Delete children's children first (grandchildren)
        for (PlanEntity child : children) {
            if ("planservice".equals(child.getObjectType())) {
                // Delete this PlanService's children (Service and CostShares)
                repository.deleteByParentId(child.getId());
            }
        }

        repository.deleteByParentId(planId); // Delete direct children
        repository.deleteById(planId); // Delete Plan itself
        log.debug("Deleted Plan and all related documents: {}", planId);
    }

    /**
     * For patch API, merge existing node with new nodes
     */
    private JsonNode deepMerge(JsonNode existing, JsonNode patch) {
        if (!existing.isObject()) return patch;

        ObjectNode merged = ((ObjectNode) existing).deepCopy();

        Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode patchValue = entry.getValue();

            if (merged.has(fieldName)) {
                JsonNode existingValue = merged.get(fieldName);

                if (existingValue.isObject() && patchValue.isObject()) {
                    merged.set(fieldName, deepMerge(existingValue, patchValue));
                } else {
                    merged.set(fieldName, patchValue);
                }
            } else {
                merged.set(fieldName, patchValue);
            }
        }
        return merged;
    }
}
