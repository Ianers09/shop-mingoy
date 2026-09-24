package edu.cit.mingoy.notification;

import edu.cit.mingoy.supplier.events.SupplierOrderCancelled;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SupplierCancellationNotificationListener {

    private final JdbcTemplate jdbcTemplate;

    SupplierCancellationNotificationListener(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener
    public void handleSupplierOrderCancelled(
            SupplierOrderCancelled event
    ) {
        String message =
                "Supplier order " +
                        event.poNumber() +
                        " for product " +
                        event.productId() +
                        " was cancelled.";

        jdbcTemplate.update(
                """
                INSERT INTO notifications (message)
                VALUES (?)
                """,
                message
        );
    }
}