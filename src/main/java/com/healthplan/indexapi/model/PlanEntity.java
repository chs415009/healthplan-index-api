package com.healthplan.indexapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Generic MongoDB Document Entity
 * Used to store all types of objects (Plan, PlanCostShares, LinkedPlanService, Service)
 */
@Document(collection = "plans") // Maps this Java class to the "plans" collection in MongoDB for persistence.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanEntity {

    // Document ID (same as objectId)
    @Id
    private String id;

    // Object type (plan, membercostshare, planservice, service)
    private String objectType;

    // Parent document ID (if this is a child document)
    private String parentId;

    // All other properties stored as Map
    private Map<String, Object> attributes;
}
