package io.github.devandref.inventory_service.core.consumer;

import io.github.devandref.inventory_service.core.service.InventoryService;
import io.github.devandref.inventory_service.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class InventoryServiceConsumer {

    private final JsonUtil jsonUtil;
    private final InventoryService inventoryService;

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${spring.kafka.topic.orchestrator}"
    )
    public void consumerOrchestratorEvent(String payload) {
        log.info("Receiving event {} from orchestrator topic", payload);
        var event = jsonUtil.toEvent(payload);
        log.info("Event object {}", event);
    }

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${spring.kafka.topic.inventory-success}"
    )
    public void consumerInventorySuccessEvent(String payload) {
        log.info("Receiving event {} from inventory-success topic", payload);
        var event = jsonUtil.toEvent(payload);
        inventoryService.updateInventory(event);
    }

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${spring.kafka.topic.inventory-fail}"
    )
    public void consumerInventoryFailEvent(String payload) {
        log.info("Receiving event {} from inventory-fail topic", payload);
        var event = jsonUtil.toEvent(payload);
        inventoryService.rollbackInventory(event);
    }



}
