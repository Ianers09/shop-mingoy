package edu.cit.mingoy.notification;

import edu.cit.mingoy.shop.events.LowStock;
import edu.cit.mingoy.shop.events.OrderPlaced;
import edu.cit.mingoy.shop.events.OrderRejected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void handleOrderPlaced(OrderPlaced event) {

        notificationService.createNotification(
                "Order #" + event.orderId() + " confirmed"
        );
    }

    @EventListener
    public void handleOrderRejected(OrderRejected event) {

        notificationService.createNotification(
                "Order #" + event.orderId()
                        + " rejected: "
                        + event.reason()
        );
    }

    @EventListener
    public void handleLowStock(LowStock event) {

        notificationService.createNotification(
                "Low stock: "
                        + event.productId()
                        + " has "
                        + event.remainingStock()
                        + " remaining - reorder needed"
        );
    }
}