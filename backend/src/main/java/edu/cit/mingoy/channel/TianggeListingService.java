package edu.cit.mingoy.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.mingoy.inventory.InventoryItem;
import edu.cit.mingoy.inventory.InventoryService;
import edu.cit.mingoy.supplier.SupplierGateway;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class TianggeListingService {

    private final TianggeClient tianggeClient;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final ObjectMapper objectMapper;

    TianggeListingService(
            TianggeClient tianggeClient,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            ObjectMapper objectMapper
    ) {
        this.tianggeClient = tianggeClient;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    void publishInitialMarketplaceState() {
        publishListings();
        publishStock();
    }

    void publishStock() {
        try {
            List<Map<String, Object>> payload = new ArrayList<>();
            for (InventoryItem item : inventoryService.getAllItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sellerSku", item.getProductId());
                row.put("available", item.getStock());
                payload.add(row);
            }
            tianggeClient.publishStock(objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish Tiangge stock", exception);
        }
    }

    private void publishListings() {
        try {
            List<Map<String, Object>> payload = new ArrayList<>();
            for (InventoryItem item : inventoryService.getAllItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sellerSku", item.getProductId());
                row.put("title", item.getName());
                row.put("supplierSku", supplierGateway.getSupplierSku(item.getProductId()));
                payload.add(row);
            }
            tianggeClient.publishListings(objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish Tiangge listings", exception);
        }
    }
}
