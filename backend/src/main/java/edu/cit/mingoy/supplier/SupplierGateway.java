package edu.cit.mingoy.supplier;

/**
 * Application-facing gateway for supplier integration.
 *
 * The rest of the application only works with our own
 * product IDs, units, and supplier result types.
 *
 * LegacySupply-specific XML, SKUs, UOM values, HTTP handling,
 * sessions, and status codes must remain inside the supplier module.
 */
public interface SupplierGateway {

    /**
     * Places a replenishment order with the external supplier.
     *
     * @param productId our internal inventory product ID
     * @param unitsNeeded number of individual units our inventory needs
     * @param buyerRef our unique reference for this replenishment
     * @param requestId stable idempotency key for this replenishment
     * @return our own supplier order result
     */
    SupplierOrderResult placeOrder(
            String productId,
            int unitsNeeded,
            String buyerRef,
            String requestId
    );
}