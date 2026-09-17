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
    public Order createOrder(
            List<OrderController.OrderItemRequest> requestItems
    ) {

        /*
         * STEP 1:
         * Validate that the order contains items.
         */
        if (requestItems == null || requestItems.isEmpty()) {

            Order order = new Order(
                    "REJECTED",
                    "Order must contain at least one item"
            );

            Order saved = orderRepository.save(order);

            eventPublisher.publishEvent(
                    new OrderRejected(
                            saved.getOrderId(),
                            saved.getReason()
                    )
            );

            return saved;
        }

        /*
         * STEP 2:
         * Validate EVERY item BEFORE reserving ANY inventory.
         *
         * This is important for atomicity.
         * If one item fails validation, nothing is reserved.
         */
        List<String> validationErrors = new ArrayList<>();

        for (OrderController.OrderItemRequest requestItem : requestItems) {

            if (requestItem == null) {
                validationErrors.add("Invalid order item");
                continue;
            }

            if (requestItem.productId() == null
                    || requestItem.productId().isBlank()) {

                validationErrors.add(
                        "Product ID must not be empty"
                );

                continue;
            }

            if (requestItem.quantity() <= 0) {

                validationErrors.add(
                        requestItem.productId()
                                + ": Quantity must be greater than zero"
                );

                continue;
            }

            InventoryItem inventoryItem =
                    inventoryService.getItem(
                            requestItem.productId()
                    );

            if (inventoryItem == null) {

                validationErrors.add(
                        requestItem.productId()
                                + ": Product not found"
                );

                continue;
            }

            if (inventoryItem.getStock()
                    < requestItem.quantity()) {

                validationErrors.add(
                        requestItem.productId()
                                + ": Insufficient stock"
                );
            }
        }

        /*
         * STEP 3:
         * If ANY item failed validation,
         * reject the ENTIRE order.
         *
         * No inventory reservation has happened yet.
         */
        if (!validationErrors.isEmpty()) {

            String reason =
                    String.join("; ", validationErrors);

            Order order = new Order(
                    "REJECTED",
                    reason
            );

            for (OrderController.OrderItemRequest requestItem
                    : requestItems) {

                if (requestItem == null) {
                    continue;
                }

                if (requestItem.productId() == null
                        || requestItem.productId().isBlank()) {
                    continue;
                }

                if (requestItem.quantity() <= 0) {
                    continue;
                }

                order.addItem(
                        new OrderItem(
                                requestItem.productId(),
                                requestItem.quantity()
                        )
                );
            }

            Order saved = orderRepository.save(order);

            eventPublisher.publishEvent(
                    new OrderRejected(
                            saved.getOrderId(),
                            saved.getReason()
                    )
            );

            return saved;
        }

        /*
         * STEP 4:
         * All items passed validation.
         *
         * We can now reserve every item.
         */
        Order order = new Order(
                "CONFIRMED",
                "Order confirmed"
        );

        for (OrderController.OrderItemRequest requestItem
                : requestItems) {

            InventoryItem reserved =
                    inventoryService.reserve(
                            requestItem.productId(),
                            requestItem.quantity()
                    );

            /*
             * If inventory unexpectedly changed between validation
             * and reservation, throw an exception.
             *
             * Because this method is @Transactional, previous
             * inventory changes in this transaction are rolled back.
             */
            if (reserved == null) {

                throw new IllegalStateException(
                        "Unable to reserve inventory for "
                                + requestItem.productId()
                );
            }

            order.addItem(
                    new OrderItem(
                            requestItem.productId(),
                            requestItem.quantity()
                    )
            );
        }

        /*
         * STEP 5:
         * Save the confirmed order and publish the event.
         */
        Order saved =
                orderRepository.save(order);

        eventPublisher.publishEvent(
                new OrderPlaced(
                        saved.getOrderId()
                )
        );

        return saved;
    }

    @Transactional
    public Order cancelOrder(Long orderId) {

        Order order =
                orderRepository.findById(orderId)
                        .orElse(null);

        /*
         * Controller converts null into HTTP 404.
         */
        if (order == null) {
            return null;
        }

        /*
         * Controller converts this exception into HTTP 409.
         */
        if ("CANCELLED".equals(order.getStatus())) {

            throw new IllegalStateException(
                    "Order is already CANCELLED"
            );
        }

        /*
         * Restock every line item that was reserved.
         */
        for (OrderItem item : order.getItems()) {

            InventoryItem restocked =
                    inventoryService.restock(
                            item.getProductId(),
                            item.getQuantity()
                    );

            if (restocked == null) {

                throw new IllegalStateException(
                        "Unable to restock "
                                + item.getProductId()
                );
            }
        }

        order.setStatus("CANCELLED");

        order.setReason(
                "Order cancelled and inventory restocked"
        );

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderController.OrderHistory> getOrderHistory() {

        List<Order> orders =
                orderRepository.findAll();

        List<OrderController.OrderHistory> history =
                new ArrayList<>();

        for (Order order : orders) {

            List<OrderController.OrderHistoryItem> items =
                    new ArrayList<>();

            for (OrderItem item : order.getItems()) {

                items.add(
                        new OrderController.OrderHistoryItem(
                                item.getProductId(),
                                item.getQuantity()
                        )
                );
            }

            history.add(
                    new OrderController.OrderHistory(
                            order.getOrderId(),
                            order.getStatus(),
                            order.getReason(),
                            order.getCreatedAt().toString(),
                            items
                    )
            );
        }

        return history;
    }
}