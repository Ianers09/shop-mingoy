package edu.cit.mingoy.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.mingoy.inventory.InventoryService;
import edu.cit.mingoy.shop.OrderController;
import edu.cit.mingoy.shop.OrderService;
import edu.cit.mingoy.supplier.events.SupplierOrderDelivered;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class TianggeBackorderListener {

    private final TianggeOrderRepository orderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final TianggeClient tianggeClient;
    private final TianggeListingService listingService;
    private final ObjectMapper objectMapper;

    TianggeBackorderListener(
            TianggeOrderRepository orderRepository,
            OrderService orderService,
            InventoryService inventoryService,
            TianggeClient tianggeClient,
            TianggeListingService listingService,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.tianggeClient = tianggeClient;
        this.listingService = listingService;
        this.objectMapper = objectMapper;
    }

    @EventListener
    @Order(2)
    void handleSupplierDelivery(
            SupplierOrderDelivered event
    ) {
        for (TianggeOrderEntity order :
                orderRepository.findByStatus("BACKORDERED")) {

            try {
                List<OrderController.OrderItemRequest> items =
                        objectMapper.readValue(
                                order.getLinesJson(),
                                new TypeReference<>() {
                                }
                        );

                boolean ready = true;

                for (OrderController.OrderItemRequest item :
                        items) {

                    var inventory =
                            inventoryService.getItem(
                                    item.productId()
                            );

                    if (inventory == null
                            || inventory.getStock()
                            < item.quantity()) {
                        ready = false;
                        break;
                    }
                }

                if (!ready) {
                    continue;
                }

                var localOrder =
                        orderService.fulfillBackorderedOrder(
                                order.getLocalOrderId()
                        );

                if (localOrder == null) {
                    continue;
                }

                order.setStatus(
                        "RESOLUTION_PENDING"
                );

                orderRepository.saveAndFlush(order);

                try {
                    tianggeClient.resolve(
                            order.getOrderId(),
                            "{\"status\":\"ACCEPTED\"}"
                    );

                    order.setStatus("ACCEPTED");
                    orderRepository.save(order);
                    listingService.publishStock();

                } catch (Exception exception) {
                    System.out.println(
                            "TIANGGE_RESOLUTION_PENDING="
                                    + exception.getMessage()
                    );
                }

            } catch (Exception exception) {
                System.out.println(
                        "TIANGGE_BACKORDER_ERROR="
                                + exception.getMessage()
                );
            }
        }
    }
}