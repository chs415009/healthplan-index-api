package com.healthplan.indexapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Plan Queue Message model for RabbitMQ
 * Used by QueueService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanQueueMessage implements Serializable {

    // INDEX, UPDATE, DELETE
    private String operation;

    private String objectId;

    // Plan JSON Object -> String. Only INDEX and UPDATE need it
    private String jsonData;
}
