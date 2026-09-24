package edu.cit.mingoy.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrderEntity, Long> {

    Optional<SupplierOrderEntity> findByBuyerRef(String buyerRef);

    Optional<SupplierOrderEntity> findByRequestId(String requestId);

    Optional<SupplierOrderEntity> findByPoNumber(String poNumber);

    List<SupplierOrderEntity> findByStatus(SupplierOrderStatus status);
}