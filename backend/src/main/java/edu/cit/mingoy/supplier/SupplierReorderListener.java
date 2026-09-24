package edu.cit.mingoy.supplier;

import edu.cit.mingoy.shop.events.LowStock;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class SupplierReorderListener {

    private final SupplierGateway supplierGateway;

    SupplierReorderListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void handleLowStock(LowStock event) {

        /*
         * The LowStock event tells us the remaining inventory.
         *
         * Reorder enough supplier cases to restore inventory
         * back above the low-stock threshold.
         *
         * Example:
         *
         * threshold = 5
         * remaining = 2
         *
         * units needed = 5 - 2 = 3
         *
         * SupplierGateway will convert those units into
         * whole supplier cases.
         */

        int threshold = event.threshold();

        int remainingStock = event.remainingStock();

        int unitsNeeded = threshold - remainingStock;

        if (unitsNeeded <= 0) {
            return;
        }

        /*
         * Generate one stable identity for this reorder.
         *
         * The same buyer reference and request ID are used
         * if the supplier order is retried later.
         */
        String reorderId = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String buyerRef = "RO-" + reorderId;

        String requestId = buyerRef;

        supplierGateway.placeOrder(
                event.productId(),
                unitsNeeded,
                buyerRef,
                requestId
        );
    }
}