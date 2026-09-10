package edu.cit.mingoy.shop;

import edu.cit.mingoy.inventory.InventoryItem;
import edu.cit.mingoy.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public OrderService(
            OrderRepository orderRepository,
            InventoryService inventoryService
    ) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Order createOrder(String productId, int quantity) {

        InventoryItem item = inventoryService.getItem(productId);

        if (item == null) {
            Order order = new Order(
                    productId,
                    quantity,
                    "REJECTED",
                    "Product not found"
            );

            return orderRepository.save(order);
        }

        if (quantity <= 0) {
            Order order = new Order(
                    productId,
                    quantity,
                    "REJECTED",
                    "Quantity must be greater than zero"
            );

            return orderRepository.save(order);
        }

        if (item.getStock() < quantity) {
            Order order = new Order(
                    productId,
                    quantity,
                    "REJECTED",
                    "Insufficient stock"
            );

            return orderRepository.save(order);
        }

        InventoryItem reservedItem =
                inventoryService.reserve(productId, quantity);

        if (reservedItem == null) {
            Order order = new Order(
                    productId,
                    quantity,
                    "REJECTED",
                    "Unable to reserve inventory"
            );

            return orderRepository.save(order);
        }

        Order order = new Order(
                productId,
                quantity,
                "CONFIRMED",
                "Order confirmed"
        );

        return orderRepository.save(order);
    }
}