package io.github.devandref.inventory_service.core.repository;

import io.github.devandref.inventory_service.core.model.OrderInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderInventoryRepository extends JpaRepository<OrderInventory, Integer> {

    Boolean existsByOrderIdAndTransactionId(String orderId, String transactionId);
    List<OrderInventory> findByOrderIdAndTransactionId(String orderId, String transactionId);

}
