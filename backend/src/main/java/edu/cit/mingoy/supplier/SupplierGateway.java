package edu.cit.mingoy.supplier;

public interface SupplierGateway {

    SupplierOrderResult placeOrder(
            String productId,
            int unitsNeeded,
            String buyerRef,
            String requestId
    );

    String getSupplierSku(String productId);
}
