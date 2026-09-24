package edu.cit.mingoy.supplier;

/**
 * Our application's representation of a supplier purchase order result.
 *
 * This class deliberately contains no LegacySupply XML types,
 * LegacySupply status codes, or supplier-specific objects.
 */
public record SupplierOrderResult(
        Long supplierOrderId,
        String productId,
        String buyerRef,
        String requestId,
        String poNumber,
        int cases,
        int units,
        SupplierOrderStatus status
) {
}