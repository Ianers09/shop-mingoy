package edu.cit.mingoy.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.mingoy.inventory.InventoryService;
import edu.cit.mingoy.shop.OrderController;
import edu.cit.mingoy.shop.OrderRepository;
import edu.cit.mingoy.shop.OrderService;
import edu.cit.mingoy.supplier.SupplierGateway;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class TianggeFeedScheduler {

    private final TianggeClient tianggeClient;
    private final TianggeStateRepository stateRepository;
    private final TianggeEventRepository eventRepository;
    private final TianggeOrderRepository orderRepository;
    private final OrderRepository localOrderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeListingService listingService;
    private final ObjectMapper objectMapper;

    TianggeFeedScheduler(
            TianggeClient tianggeClient,
            TianggeStateRepository stateRepository,
            TianggeEventRepository eventRepository,
            TianggeOrderRepository orderRepository,
            OrderRepository localOrderRepository,
            OrderService orderService,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            TianggeListingService listingService,
            ObjectMapper objectMapper
    ) {
        this.tianggeClient = tianggeClient;
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.localOrderRepository = localOrderRepository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.listingService = listingService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 5000)
    void pollFeed() {
        try {
            retryPendingActions();

            TianggeStateEntity state = stateRepository.findById(1)
                    .orElseGet(() -> stateRepository.save(
                            new TianggeStateEntity(1, 0L)
                    ));

            String response = tianggeClient.feed(
                    state.getCursor(),
                    50
            );

            JsonNode root = objectMapper.readTree(response);
            JsonNode events = root.path("events");

            long nextCursor = root.path("nextCursor")
                    .asLong(state.getCursor());

            for (JsonNode event : events) {
                processEvent(event);
            }

            state.setCursor(nextCursor);
            stateRepository.save(state);

        } catch (Exception exception) {
            System.out.println(
                    "TIANGGE_FEED_ERROR="
                            + exception.getMessage()
            );
        }
    }

    void processEvent(JsonNode event) {
        String eventId = event.path("eventId").asText(null);
        long seq = event.path("seq").asLong();
        String type = event.path("type").asText("");

        if (eventId == null || eventId.isBlank()) {
            return;
        }

        boolean alreadyRecorded =
                eventRepository.existsByEventId(eventId);

        if (alreadyRecorded) {
            retryEvent(event);
            return;
        }

        try {
            if ("ORDER_PLACED".equals(type)) {
                handleOrderPlaced(event);
            } else if ("ORDER_CANCELLED".equals(type)) {
                handleOrderCancelled(event);
            }

            eventRepository.save(
                    new TianggeEventEntity(
                            eventId,
                            seq,
                            type
                    )
            );

        } catch (Exception exception) {
            System.out.println(
                    "TIANGGE_EVENT_ERROR="
                            + exception.getMessage()
            );
        }
    }

    private void retryEvent(JsonNode event) {
        String type = event.path("type").asText("");

        try {
            if ("ORDER_PLACED".equals(type)) {
                handleOrderPlaced(event);
            } else if ("ORDER_CANCELLED".equals(type)) {
                handleOrderCancelled(event);
            }
        } catch (Exception exception) {
            System.out.println(
                    "TIANGGE_EVENT_RETRY_ERROR="
                            + exception.getMessage()
            );
        }
    }

    private void retryPendingActions() {
        for (TianggeOrderEntity order :
                orderRepository.findByStatus("DECISION_PENDING")) {
            try {
                sendPendingDecision(order);
            } catch (Exception exception) {
                System.out.println(
                        "TIANGGE_PENDING_DECISION_ERROR="
                                + exception.getMessage()
                );
            }
        }

        for (TianggeOrderEntity order :
                orderRepository.findByStatus("CANCELLATION_PENDING")) {
            try {
                sendPendingCancellation(order);
            } catch (Exception exception) {
                System.out.println(
                        "TIANGGE_PENDING_CANCELLATION_ERROR="
                                + exception.getMessage()
                );
            }
        }

        for (TianggeOrderEntity order :
                orderRepository.findByStatus("RESOLUTION_PENDING")) {
            try {
                sendPendingResolution(order);
            } catch (Exception exception) {
                System.out.println(
                        "TIANGGE_PENDING_RESOLUTION_ERROR="
                                + exception.getMessage()
                );
            }
        }
    }

    private void handleOrderPlaced(JsonNode event) {
        String orderId =
                event.path("orderId").asText();

        TianggeOrderEntity existing =
                orderRepository.findByOrderId(orderId).orElse(null);

        if (existing != null) {
            if ("DECISION_PENDING".equals(existing.getStatus())) {
                sendPendingDecision(existing);
            }
            return;
        }

        List<OrderController.OrderItemRequest> items =
                new ArrayList<>();

        boolean allAvailable = true;

        for (JsonNode line : event.path("lines")) {
            String productId =
                    line.path("sellerSku").asText();

            int quantity =
                    line.path("qty").asInt();

            items.add(
                    new OrderController.OrderItemRequest(
                            productId,
                            quantity
                    )
            );

            var inventory =
                    inventoryService.getItem(productId);

            if (inventory == null
                    || inventory.getStock() < quantity) {
                allAvailable = false;
            }
        }

        String linesJson;

        try {
            linesJson =
                    objectMapper.writeValueAsString(items);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        if (allAvailable) {
            var localOrder =
                    orderService.createOrder(items);

            String shopOrderId =
                    "SO-" + localOrder.getOrderId();

            TianggeOrderEntity tianggeOrder =
                    new TianggeOrderEntity(
                            orderId,
                            event.path("eventId").asText(),
                            localOrder.getOrderId(),
                            shopOrderId,
                            "DECISION_PENDING",
                            linesJson
                    );

            tianggeOrder =
                    orderRepository.saveAndFlush(tianggeOrder);

            sendPendingDecision(tianggeOrder);

            return;
        }

        for (OrderController.OrderItemRequest item : items) {
            var inventory =
                    inventoryService.getItem(item.productId());

            int current =
                    inventory == null
                            ? 0
                            : inventory.getStock();

            int missing =
                    item.quantity() - current;

            if (missing > 0) {
                String ref =
                        "TG-"
                                + orderId
                                + "-"
                                + item.productId();

                supplierGateway.placeOrder(
                        item.productId(),
                        missing,
                        ref,
                        ref
                );
            }
        }

        var localOrder =
                orderService.createBackorderedOrder(
                        items,
                        "Tiangge order waiting for supplier delivery"
                );

        String shopOrderId =
                "SO-" + localOrder.getOrderId();

        TianggeOrderEntity tianggeOrder =
                new TianggeOrderEntity(
                        orderId,
                        event.path("eventId").asText(),
                        localOrder.getOrderId(),
                        shopOrderId,
                        "DECISION_PENDING",
                        linesJson
                );

        tianggeOrder =
                orderRepository.saveAndFlush(tianggeOrder);

        sendPendingDecision(tianggeOrder);
    }

    private void sendPendingDecision(
            TianggeOrderEntity tianggeOrder
    ) {
        var localOrder =
                localOrderRepository.findById(
                        tianggeOrder.getLocalOrderId()
                ).orElse(null);

        if (localOrder == null) {
            return;
        }

        String decision;

        if ("CONFIRMED".equals(localOrder.getStatus())) {
            decision = "ACCEPTED";
        } else if ("BACKORDERED".equals(localOrder.getStatus())) {
            decision = "BACKORDERED";
        } else {
            decision = "REJECTED";
        }

        String reason =
                localOrder.getReason();

        String body =
                "{\"decision\":\""
                        + decision
                        + "\",\"shopOrderId\":\""
                        + tianggeOrder.getShopOrderId()
                        + "\",\"reason\":\""
                        + escapeJson(reason)
                        + "\"}";

        tianggeClient.decide(
                tianggeOrder.getOrderId(),
                body
        );

        tianggeOrder.setStatus(decision);
        orderRepository.save(tianggeOrder);

        if ("ACCEPTED".equals(decision)) {
            publishStockSafely();
        }
    }

    private void handleOrderCancelled(JsonNode event) {
        String orderId =
                event.path("orderId").asText();

        TianggeOrderEntity tianggeOrder =
                orderRepository.findByOrderId(orderId)
                        .orElse(null);

        if (tianggeOrder == null) {
            return;
        }

        if ("CANCELLATION_PENDING".equals(
                tianggeOrder.getStatus())) {
            sendPendingCancellation(tianggeOrder);
            return;
        }

        if (!"ACCEPTED".equals(
                tianggeOrder.getStatus())) {
            return;
        }

        if (tianggeOrder.getLocalOrderId() == null) {
            return;
        }

        orderService.cancelOrder(
                tianggeOrder.getLocalOrderId()
        );

        tianggeOrder.setStatus(
                "CANCELLATION_PENDING"
        );

        orderRepository.saveAndFlush(tianggeOrder);

        sendPendingCancellation(tianggeOrder);
    }

    private void sendPendingCancellation(
            TianggeOrderEntity tianggeOrder
    ) {
        tianggeClient.confirmCancellation(
                tianggeOrder.getOrderId(),
                "{\"restocked\":true}"
        );

        tianggeOrder.setStatus(
                "CANCELLED_BY_CUSTOMER"
        );

        orderRepository.save(tianggeOrder);

        publishStockSafely();
    }

    private void sendPendingResolution(
            TianggeOrderEntity tianggeOrder
    ) {
        tianggeClient.resolve(
                tianggeOrder.getOrderId(),
                "{\"status\":\"ACCEPTED\"}"
        );

        tianggeOrder.setStatus("ACCEPTED");
        orderRepository.save(tianggeOrder);

        publishStockSafely();
    }

    private void publishStockSafely() {
        try {
            listingService.publishStock();
        } catch (Exception exception) {
            System.out.println(
                    "TIANGGE_STOCK_ERROR="
                            + exception.getMessage()
            );
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}