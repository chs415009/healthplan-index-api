package com.healthplan.indexapi.service;

import com.healthplan.indexapi.model.PlanQueueMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Responsible for sending messages to RabbitMQ
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${queue.plan.index}")
    private String indexQueueName;

    @Value("${queue.plan.update}")
    private String updateQueueName;

    @Value("${queue.plan.delete}")
    private String deleteQueueName;

    /**
     * Send INDEX message (for POST operations)
     */
    public void sendIndexMessage(String objectId, String jsonData) {
        PlanQueueMessage message = PlanQueueMessage.builder()
                .operation("INDEX")
                .objectId(objectId)
                .jsonData(jsonData)
                .build();

        rabbitTemplate.convertAndSend(indexQueueName, message);
        log.info("Sent INDEX message to queue: objectId={}", objectId);
    }

    /**
     * Send UPDATE message (for PATCH operations)
     */
    public void sendUpdateMessage(String objectId, String jsonData) {
        PlanQueueMessage message = PlanQueueMessage.builder()
                .operation("UPDATE")
                .objectId(objectId)
                .jsonData(jsonData)
                .build();

        rabbitTemplate.convertAndSend(updateQueueName, message);
        log.info("Sent UPDATE message to queue: objectId={}", objectId);
    }

    /**
     * Send DELETE message (for DELETE operations)
     */
    public void sendDeleteMessage(String objectId) {
        PlanQueueMessage message = PlanQueueMessage.builder()
                .operation("DELETE")
                .objectId(objectId)
                .jsonData(null)  // No JSON data needed for DELETE
                .build();

        rabbitTemplate.convertAndSend(deleteQueueName, message);
        log.info("Sent DELETE message to queue: objectId={}", objectId);
    }
}
