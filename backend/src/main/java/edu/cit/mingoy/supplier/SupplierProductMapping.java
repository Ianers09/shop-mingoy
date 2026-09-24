package edu.cit.mingoy.supplier;

import java.util.Map;

final class SupplierProductMapping {

    private static final Map<String, ProductMapping> PRODUCTS = Map.of(
            "P100", new ProductMapping("FFN-5102", 6),
            "P200", new ProductMapping("FFN-2651", 20),
            "P300", new ProductMapping("FFN-4541", 20)
    );

    private SupplierProductMapping() {
    }

    static ProductMapping get(String productId) {
        ProductMapping mapping = PRODUCTS.get(productId);

        if (mapping == null) {
            throw new IllegalArgumentException(
                    "No supplier mapping exists for product: " + productId
            );
        }

        return mapping;
    }

    record ProductMapping(
            String supplierSku,
            int packSize
    ) {
    }
}