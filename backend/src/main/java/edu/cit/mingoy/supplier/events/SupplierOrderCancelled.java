package edu.cit.mingoy.supplier.events;

public record SupplierOrderCancelled(
        String productId,
        String poNumber
) {
}