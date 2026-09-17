package edu.cit.mingoy.shop;

import edu.cit.mingoy.inventory.InventoryItem;
import edu.cit.mingoy.inventory.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5173")
public class OrderController {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    public OrderController(
            OrderService orderService,
            InventoryService inventoryService
    ) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderRequest request
    ) {

        Order order = orderService.createOrder(request.items());

        List<ItemOutcome> outcomes = new ArrayList<>();

        for (OrderItemRequest item : request.items()) {
            String outcome;

            if ("CONFIRMED".equals(order.getStatus())) {
                outcome = "RESERVED";
            } else {
                outcome = "REJECTED";
            }

            outcomes.add(
                    new ItemOutcome(
                            item.productId(),
                            item.quantity(),
                            outcome
                    )
            );
        }

        List<InventoryItem> inventory =
                inventoryService.getAllItems();

        OrderResponse response = new OrderResponse(
                order.getOrderId(),
                order.getStatus(),
                order.getReason(),
                outcomes,
                inventory
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderHistory>> getOrders() {

        List<OrderHistory> history =
                orderService.getOrderHistory();

        return ResponseEntity.ok(history);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Long orderId
    ) {

        try {

            Order order = orderService.cancelOrder(orderId);

            if (order == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(
                                new ErrorResponse(
                                        "Order not found"
                                )
                        );
            }

            List<InventoryItem> inventory =
                    inventoryService.getAllItems();

            return ResponseEntity.ok(
                    new CancelResponse(
                            order.getOrderId(),
                            order.getStatus(),
                            order.getReason(),
                            inventory
                    )
            );

        } catch (IllegalStateException exception) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            new ErrorResponse(
                                    exception.getMessage()
                            )
                    );
        }
    }

    public record OrderRequest(
            List<OrderItemRequest> items
    ) {
    }

    public record OrderItemRequest(
            String productId,
            int quantity
    ) {
    }

    public record ItemOutcome(
            String productId,
            int quantity,
            String outcome
    ) {
    }

    public record OrderResponse(
            Long orderId,
            String status,
            String reason,
            List<ItemOutcome> items,
            List<InventoryItem> inventory
    ) {
    }

    public record OrderHistory(
            Long orderId,
            String status,
            String reason,
            String createdAt,
            List<OrderHistoryItem> items
    ) {
    }

    public record OrderHistoryItem(
            String productId,
            int quantity
    ) {
    }

    public record CancelResponse(
            Long orderId,
            String status,
            String reason,
            List<InventoryItem> inventory
    ) {
    }

    public record ErrorResponse(
            String error
    ) {
    }
}