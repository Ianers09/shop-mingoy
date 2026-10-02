package edu.cit.mingoy.shop;

import edu.cit.mingoy.inventory.InventoryItem;
import edu.cit.mingoy.inventory.InventoryService;
import edu.cit.mingoy.shop.events.OrderPlaced;
import edu.cit.mingoy.shop.events.OrderRejected;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            InventoryService inventoryService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order createOrder(List<OrderController.OrderItemRequest> requestItems) {
        if (requestItems == null || requestItems.isEmpty()) {
            return saveRejected("Order must contain at least one item", List.of());
        }

        List<String> validationErrors = new ArrayList<>();
        for (OrderController.OrderItemRequest requestItem : requestItems) {
            if (requestItem == null) {
                validationErrors.add("Invalid order item");
                continue;
            }
            if (requestItem.productId() == null || requestItem.productId().isBlank()) {
                validationErrors.add("Product ID must not be empty");
                continue;
            }
            if (requestItem.quantity() <= 0) {
                validationErrors.add(requestItem.productId() + ": Quantity must be greater than zero");
                continue;
            }
            InventoryItem inventoryItem = inventoryService.getItem(requestItem.productId());
            if (inventoryItem == null) {
                validationErrors.add(requestItem.productId() + ": Product not found");
                continue;
            }
            if (inventoryItem.getStock() < requestItem.quantity()) {
                validationErrors.add(requestItem.productId() + ": Insufficient stock");
            }
        }

        if (!validationErrors.isEmpty()) {
            return saveRejected(
                    String.join("; ", validationErrors),
                    requestItems
            );
        }

        Order order = new Order("CONFIRMED", "Order confirmed");
        for (OrderController.OrderItemRequest requestItem : requestItems) {
            InventoryItem reserved = inventoryService.reserve(
                    requestItem.productId(),
                    requestItem.quantity()
            );
            if (reserved == null) {
                throw new IllegalStateException(
                        "Unable to reserve inventory for " + requestItem.productId()
                );
            }
            order.addItem(new OrderItem(requestItem.productId(), requestItem.quantity()));
        }

        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlaced(saved.getOrderId()));
        return saved;
    }

    @Transactional
    public Order createBackorderedOrder(
            List<OrderController.OrderItemRequest> requestItems,
            String reason
    ) {
        Order order = new Order("BACKORDERED", reason);
        for (OrderController.OrderItemRequest requestItem : requestItems) {
            order.addItem(new OrderItem(requestItem.productId(), requestItem.quantity()));
        }
        return orderRepository.save(order);
    }

    @Transactional
    public Order fulfillBackorderedOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !"BACKORDERED".equals(order.getStatus())) {
            return null;
        }

        for (OrderItem item : order.getItems()) {
            InventoryItem inventoryItem = inventoryService.getItem(item.getProductId());
            if (inventoryItem == null || inventoryItem.getStock() < item.getQuantity()) {
                return null;
            }
        }

        for (OrderItem item : order.getItems()) {
            if (inventoryService.reserve(item.getProductId(), item.getQuantity()) == null) {
                throw new IllegalStateException(
                        "Unable to reserve inventory for " + item.getProductId()
                );
            }
        }

        order.setStatus("CONFIRMED");
        order.setReason("Backorder fulfilled");
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlaced(saved.getOrderId()));
        return saved;
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Order is already CANCELLED");
        }
        if ("CONFIRMED".equals(order.getStatus())) {
            for (OrderItem item : order.getItems()) {
                InventoryItem restocked = inventoryService.restock(
                        item.getProductId(),
                        item.getQuantity()
                );
                if (restocked == null) {
                    throw new IllegalStateException(
                            "Unable to restock " + item.getProductId()
                    );
                }
            }
        }
        order.setStatus("CANCELLED");
        order.setReason("Order cancelled and inventory restocked");
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderController.OrderHistory> getOrderHistory() {
        List<Order> orders = orderRepository.findAll();
        List<OrderController.OrderHistory> history = new ArrayList<>();
        for (Order order : orders) {
            List<OrderController.OrderHistoryItem> items = new ArrayList<>();
            for (OrderItem item : order.getItems()) {
                items.add(new OrderController.OrderHistoryItem(item.getProductId(), item.getQuantity()));
            }
            history.add(new OrderController.OrderHistory(
                    order.getOrderId(),
                    order.getStatus(),
                    order.getReason(),
                    order.getCreatedAt().toString(),
                    items
            ));
        }
        return history;
    }

    private Order saveRejected(
            String reason,
            List<OrderController.OrderItemRequest> requestItems
    ) {
        Order order = new Order("REJECTED", reason);
        for (OrderController.OrderItemRequest requestItem : requestItems) {
            if (requestItem == null || requestItem.productId() == null || requestItem.productId().isBlank() || requestItem.quantity() <= 0) {
                continue;
            }
            order.addItem(new OrderItem(requestItem.productId(), requestItem.quantity()));
        }
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderRejected(saved.getOrderId(), saved.getReason()));
        return saved;
    }
}
