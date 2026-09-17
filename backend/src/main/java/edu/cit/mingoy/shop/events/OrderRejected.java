package edu.cit.mingoy.shop.events;

public record OrderRejected(
        Long orderId,
        String reason
) {
}