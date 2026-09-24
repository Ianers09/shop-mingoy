package edu.cit.mingoy.supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private final SupplierOrderRepository supplierOrderRepository;
    private final LegacySupplyClient legacySupplyClient;

    SupplierGatewayImpl(
            SupplierOrderRepository supplierOrderRepository,
            LegacySupplyClient legacySupplyClient
    ) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.legacySupplyClient = legacySupplyClient;
    }

    @Override
    @Transactional
    public SupplierOrderResult placeOrder(
            String productId,
            int unitsNeeded,
            String buyerRef,
            String requestId
    ) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID is required.");
        }

        if (unitsNeeded <= 0) {
            throw new IllegalArgumentException("Units needed must be greater than zero.");
        }

        if (buyerRef == null || buyerRef.isBlank()) {
            throw new IllegalArgumentException("Buyer reference is required.");
        }

        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID is required.");
        }

        var existingByRequestId =
                supplierOrderRepository.findByRequestId(requestId);

        if (existingByRequestId.isPresent()) {
            SupplierOrderEntity existing = existingByRequestId.get();

            if (existing.getStatus() != SupplierOrderStatus.PENDING) {
                return existing.toResult();
            }

            return retryPendingOrder(existing);
        }

        var existingByBuyerRef =
                supplierOrderRepository.findByBuyerRef(buyerRef);

        if (existingByBuyerRef.isPresent()) {
            SupplierOrderEntity existing = existingByBuyerRef.get();

            if (existing.getStatus() != SupplierOrderStatus.PENDING) {
                return existing.toResult();
            }

            return retryPendingOrder(existing);
        }

        SupplierProductMapping.ProductMapping mapping =
                SupplierProductMapping.get(productId);

        int packSize = mapping.packSize();

        int cases = (int) Math.ceil(
                (double) unitsNeeded / packSize
        );

        int orderedUnits = cases * packSize;

        SupplierOrderEntity supplierOrder =
                new SupplierOrderEntity(
                        productId,
                        buyerRef,
                        requestId,
                        cases,
                        orderedUnits,
                        SupplierOrderStatus.PENDING
                );

        supplierOrder =
                supplierOrderRepository.save(supplierOrder);

        return submitOrder(
                supplierOrder,
                mapping.supplierSku()
        );
    }

    private SupplierOrderResult retryPendingOrder(
            SupplierOrderEntity supplierOrder
    ) {
        SupplierProductMapping.ProductMapping mapping =
                SupplierProductMapping.get(
                        supplierOrder.getProductId()
                );

        return submitOrder(
                supplierOrder,
                mapping.supplierSku()
        );
    }

    private SupplierOrderResult submitOrder(
            SupplierOrderEntity supplierOrder,
            String supplierSku
    ) {
        try {
            LegacySupplyClient.LegacyOrderResponse response =
                    legacySupplyClient.placePurchaseOrder(
                            supplierSku,
                            supplierOrder.getCases(),
                            supplierOrder.getBuyerRef(),
                            supplierOrder.getRequestId()
                    );

            if (response.success()) {
                supplierOrder.setPoNumber(
                        response.poNumber()
                );

                supplierOrder.setStatus(
                        mapStatusCode(
                                response.supplierStatusCode()
                        )
                );
            } else {
                supplierOrder.setStatus(
                        SupplierOrderStatus.PENDING
                );
            }

        } catch (RuntimeException exception) {
            supplierOrder.setStatus(
                    SupplierOrderStatus.PENDING
            );
        }

        supplierOrderRepository.save(supplierOrder);

        return supplierOrder.toResult();
    }

    private SupplierOrderStatus mapStatusCode(
            String statusCode
    ) {
        if (statusCode == null) {
            return SupplierOrderStatus.UNKNOWN;
        }

        return switch (statusCode) {
            case "10" -> SupplierOrderStatus.ACCEPTED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }
}