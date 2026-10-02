package edu.cit.mingoy.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface TianggeOrderRepository extends JpaRepository<TianggeOrderEntity, Long> {
    Optional<TianggeOrderEntity> findByOrderId(String orderId);
    List<TianggeOrderEntity> findByStatus(String status);
}
