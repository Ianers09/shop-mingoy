package edu.cit.mingoy.channel;

import edu.cit.mingoy.supplier.events.SupplierOrderDelivered;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
class TianggeStockDeliveryListener {

    private final TianggeListingService listingService;

    TianggeStockDeliveryListener(TianggeListingService listingService) {
        this.listingService = listingService;
    }

    @EventListener
    @Order(3)
    void handleSupplierDelivery(SupplierOrderDelivered event) {
        listingService.publishStock();
    }
}
