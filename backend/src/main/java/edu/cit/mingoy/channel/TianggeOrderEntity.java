package edu.cit.mingoy.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tiangge_orders")
class TianggeOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 80)
    private String orderId;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "local_order_id")
    private Long localOrderId;

    @Column(name = "shop_order_id", length = 80)
    private String shopOrderId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT")
    private String linesJson;

    protected TianggeOrderEntity() {
    }

    TianggeOrderEntity(
            String orderId,
            String eventId,
            Long localOrderId,
            String shopOrderId,
            String status,
            String linesJson
    ) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.localOrderId = localOrderId;
        this.shopOrderId = shopOrderId;
        this.status = status;
        this.linesJson = linesJson;
    }

    String getOrderId() { return orderId; }
    Long getLocalOrderId() { return localOrderId; }
    String getShopOrderId() { return shopOrderId; }
    String getStatus() { return status; }
    String getLinesJson() { return linesJson; }
    void setStatus(String status) { this.status = status; }
}
