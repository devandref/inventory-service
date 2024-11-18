package io.github.devandref.inventory_service.core.service;

import io.github.devandref.inventory_service.config.exeception.ValidationException;
import io.github.devandref.inventory_service.core.dto.Event;
import io.github.devandref.inventory_service.core.dto.History;
import io.github.devandref.inventory_service.core.dto.Order;
import io.github.devandref.inventory_service.core.dto.OrderProducts;
import io.github.devandref.inventory_service.core.enums.ESagaStatus;
import io.github.devandref.inventory_service.core.model.Inventory;
import io.github.devandref.inventory_service.core.model.OrderInventory;
import io.github.devandref.inventory_service.core.producer.SagaProducer;
import io.github.devandref.inventory_service.core.repository.InventoryRepository;
import io.github.devandref.inventory_service.core.repository.OrderInventoryRepository;
import io.github.devandref.inventory_service.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class InventoryService {

    private static final String CURRENT_SOURCE = "INVENTORY_SERVICE";

    private final JsonUtil jsonUtil;
    private final SagaProducer sagaProducer;
    private final InventoryRepository inventoryRepository;
    private final OrderInventoryRepository orderInventoryRepository;

    public void updateInventory(Event event) {
        try {
            checkCurrentValidation(event);
            createOrderInventory(event);
            updateInventory(event.getPayload());
            handleSuccess(event);
        } catch (Exception ex) {
            log.error("Error trying to update inventory: ", ex);
            handleFailCurrentNotExecuted(event, ex.getMessage());
        }
        sagaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void createOrderInventory(Event event) {
        event
                .getPayload()
                .getProducts()
                .forEach(products -> {
                    var inventory = findInventoryByProductCode(products.getProduct().getCode());
                    var orderInventory = createOrderInventory(event, products, inventory);
                    orderInventoryRepository.save(orderInventory);
                });
    }

    private OrderInventory createOrderInventory(Event event, OrderProducts product, Inventory inventory) {
        return OrderInventory
                .builder()
                .inventory(inventory)
                .oldQuantity(inventory.getAvailable())
                .orderQuantity(product.getQuantity())
                .newQuantity(inventory.getAvailable() - product.getQuantity())
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .build();
    }

    private Inventory findInventoryByProductCode(String productCode) {
        return inventoryRepository
                .findByProductCode(productCode)
                .orElseThrow(() -> new ValidationException("Inventory not found by informed product!"));
    }

    private void checkCurrentValidation(Event event) {
        if (orderInventoryRepository.existsByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId())) {
            throw new ValidationException("There's another transactionId for this validation.");
        }
    }

    public void updateInventory(Order order) {
        order
                .getProducts()
                .forEach(product -> {
                    var inventory = findInventoryByProductCode(product.getProduct().getCode());
                    checkInventory(inventory.getAvailable(), product.getQuantity());
                    inventory.setAvailable(inventory.getAvailable() - product.getQuantity());
                    inventoryRepository.save(inventory);
                });
    }

    private void checkInventory(Integer available, Integer orderQuantity) {
        if (orderQuantity > available) {
            throw new ValidationException("Product is out of stock!");
        }
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Inventory updated successfully!");
    }

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to update inventory: ".concat(message));
    }

    public void rollbackInventory(Event event) {
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        try {
            returnInventoryToPreviousValues(event);
            addHistory(event, "Rollback executed for inventory!");
        } catch (Exception ex) {
            addHistory(event, "Rollback not executed for inventory: ".concat(ex.getMessage()));
        }
        sagaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void returnInventoryToPreviousValues(Event event) {
        orderInventoryRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .forEach(orderInventory -> {
                    var inventory = orderInventory.getInventory();
                    inventory.setAvailable(orderInventory.getOldQuantity());
                    inventoryRepository.save(inventory);
                    log.info("Restored inventory for order {} from {} to {} ",
                            event.getPayload().getId(), orderInventory.getNewQuantity(), inventory.getAvailable());
                });
        sagaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void addHistory(Event event, String msg) {
        var history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(msg)
                .createdAt(LocalDateTime.now())
                .build();
        event.addHistory(history);
    }

}
