package edu.cit.mingoy.supplier;

import edu.cit.mingoy.supplier.events.SupplierOrderDelivered;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SupplierTrackingScheduler {

    private final SupplierOrderRepository supplierOrderRepository;
    private final LegacySupplyClient legacySupplyClient;
    private final ApplicationEventPublisher eventPublisher;

    SupplierTrackingScheduler(
            SupplierOrderRepository supplierOrderRepository,
            LegacySupplyClient legacySupplyClient,
            ApplicationEventPublisher eventPublisher
    ) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.legacySupplyClient = legacySupplyClient;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 60000)
    void trackOpenOrders() {
        var openOrders = supplierOrderRepository.findAll()
                .stream()
                .filter(order ->
                        order.getPoNumber() != null &&
                        order.getStatus() != SupplierOrderStatus.DELIVERED &&
                        order.getStatus() != SupplierOrderStatus.CANCELLED
                )
                .toList();

        for (SupplierOrderEntity order : openOrders) {
            trackOrder(order);
        }
    }

    private void trackOrder(
            SupplierOrderEntity order
    ) {
        LegacySupplyClient.LegacyTrackingResponse response =
                legacySupplyClient.getPurchaseOrder(
                        order.getPoNumber()
                );

        if (!response.success()) {
            return;
        }

        SupplierOrderStatus newStatus =
                mapStatusCode(
                        response.supplierStatusCode()
                );

        SupplierOrderStatus previousStatus =
                order.getStatus();

        order.setStatus(newStatus);
        supplierOrderRepository.save(order);

        if (newStatus == SupplierOrderStatus.DELIVERED &&
                previousStatus != SupplierOrderStatus.DELIVERED) {

            eventPublisher.publishEvent(
                    new SupplierOrderDelivered(
                            order.getProductId(),
                            order.getUnits(),
                            order.getPoNumber()
                    )
            );
        }
    }

    private SupplierOrderStatus mapStatusCode(
            String statusCode
    ) {
        if (statusCode == null) {
            return SupplierOrderStatus.UNKNOWN;
        }

        return switch (statusCode) {
            case "10" -> SupplierOrderStatus.ACCEPTED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }
}