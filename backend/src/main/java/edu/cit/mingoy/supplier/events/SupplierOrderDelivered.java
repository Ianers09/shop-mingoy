package edu.cit.mingoy.supplier.events;

public record SupplierOrderDelivered(
        String productId,
        int units,
        String poNumber
) {
}