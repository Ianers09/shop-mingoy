package edu.cit.mingoy.inventory;

import edu.cit.mingoy.supplier.events.SupplierOrderDelivered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class SupplierDeliveryListener {

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void handleSupplierOrderDelivered(
            SupplierOrderDelivered event
    ) {
        InventoryItem item = inventoryService.restock(
                event.productId(),
                event.units()
        );

        if (item == null) {
            throw new IllegalStateException(
                    "Unable to restock delivered product: " +
                            event.productId()
            );
        }
    }
}