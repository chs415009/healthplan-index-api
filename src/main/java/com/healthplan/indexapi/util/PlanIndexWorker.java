package com.healthplan.indexapi.util;

import com.healthplan.indexapi.model.PlanQueueMessage;
import com.healthplan.indexapi.service.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Plan Indexing Worker (connected to RabbitMQ Server)
 * Consume messages from RabbitMQ and index to Elasticsearch
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanIndexWorker {

    private final ElasticsearchIndexService indexService;

    /**
     * @RabbitListener automatically connect to the queues on RabbitMQ Server
     */

    @RabbitListener(queues = "${queue.plan.index}")
    public void handleIndexMessage(PlanQueueMessage message) {
        log.info("Received INDEX message from queue: objectId={}", message.getObjectId());

        try {
            indexService.indexPlan(message.getObjectId(), message.getJsonData());
            log.info("Successfully processed INDEX message: objectId={}", message.getObjectId());

        } catch (Exception e) {
            log.error("Failed to process INDEX message: objectId={}, error={}",
                    message.getObjectId(), e.getMessage(), e);
            // Note: If processing fails, message will retry or go to Dead Letter Queue based on RabbitMQ config
            throw e;
        }
    }


    @RabbitListener(queues = "${queue.plan.update}")
    public void handleUpdateMessage(PlanQueueMessage message) {
        log.info("Received UPDATE message from queue: objectId={}", message.getObjectId());

        try {
            indexService.indexPlan(message.getObjectId(), message.getJsonData());
            log.info("Successfully processed UPDATE message: objectId={}", message.getObjectId());

        } catch (Exception e) {
            log.error("Failed to process UPDATE message: objectId={}, error={}",
                    message.getObjectId(), e.getMessage(), e);
            throw e;
        }
    }


    @RabbitListener(queues = "${queue.plan.delete}")
    public void handleDeleteMessage(PlanQueueMessage message) {
        log.info("Received DELETE message from queue: objectId={}", message.getObjectId());

        try {
            indexService.deletePlan(message.getObjectId());
            log.info("Successfully processed DELETE message: objectId={}", message.getObjectId());

        } catch (Exception e) {
            log.error("Failed to process DELETE message: objectId={}, error={}",
                    message.getObjectId(), e.getMessage(), e);
            throw e;
        }
    }
}
