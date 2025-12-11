package com.healthplan.indexapi.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${queue.plan.index}")
    private String indexQueueName;

    @Value("${queue.plan.update}")
    private String updateQueueName;

    @Value("${queue.plan.delete}")
    private String deleteQueueName;

    /**
     * Create Queues on RabbitMQ Server
     */
    @Bean
    public Queue indexQueue() {
        return new Queue(indexQueueName, true); // durable = true
    }

    @Bean
    public Queue updateQueue() {
        return new Queue(updateQueueName, true);
    }

    @Bean
    public Queue deleteQueue() {
        return new Queue(deleteQueueName, true);
    }

    // Convert Java object to JSON object
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate config
     * Responsible for sending messages to Queue
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter()); // use Jackson2JsonMessageConverter
        return template;
    }
}
