package edu.cit.mingoy.shop;

import edu.cit.mingoy.inventory.InventoryItem;
import edu.cit.mingoy.inventory.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        Order order = orderService.createOrder(
                request.productId(),
                request.quantity()
        );

        InventoryItem inventory =
                inventoryService.getItem(request.productId());

        OrderResponse response = new OrderResponse(
                order.getStatus(),
                order.getReason(),
                inventory
        );

        return ResponseEntity.ok(response);
    }

    public record OrderRequest(
            String productId,
            int quantity
    ) {
    }

    public record OrderResponse(
            String status,
            String reason,
            InventoryItem inventory
    ) {
    }
}