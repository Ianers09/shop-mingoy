package edu.cit.mingoy.supplier;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SupplierReorderScheduler {

    private final SupplierOrderRepository supplierOrderRepository;
    private final SupplierGateway supplierGateway;

    SupplierReorderScheduler(
            SupplierOrderRepository supplierOrderRepository,
            SupplierGateway supplierGateway
    ) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.supplierGateway = supplierGateway;
    }

    @Scheduled(fixedDelay = 30000)
    void retryPendingOrders() {
        var pendingOrders =
                supplierOrderRepository.findByStatus(
                        SupplierOrderStatus.PENDING
                );

        for (SupplierOrderEntity order : pendingOrders) {
            supplierGateway.placeOrder(
                    order.getProductId(),
                    order.getUnits(),
                    order.getBuyerRef(),
                    order.getRequestId()
            );
        }
    }
}