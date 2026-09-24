package edu.cit.mingoy.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_orders")
class SupplierOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, unique = true, length = 40)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, unique = true, length = 80)
    private String requestId;

    @Column(name = "po_number", length = 100)
    private String poNumber;

    @Column(name = "cases", nullable = false)
    private int cases;

    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SupplierOrderEntity() {
    }

    SupplierOrderEntity(
            String productId,
            String buyerRef,
            String requestId,
            int cases,
            int units,
            SupplierOrderStatus status
    ) {
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = status;

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    Long getId() {
        return id;
    }

    String getProductId() {
        return productId;
    }

    String getBuyerRef() {
        return buyerRef;
    }

    String getRequestId() {
        return requestId;
    }

    String getPoNumber() {
        return poNumber;
    }

    int getCases() {
        return cases;
    }

    int getUnits() {
        return units;
    }

    SupplierOrderStatus getStatus() {
        return status;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
        touch();
    }

    void setStatus(SupplierOrderStatus status) {
        this.status = status;
        touch();
    }

    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    SupplierOrderResult toResult() {
        return new SupplierOrderResult(
                id,
                productId,
                buyerRef,
                requestId,
                poNumber,
                cases,
                units,
                status
        );
    }
}