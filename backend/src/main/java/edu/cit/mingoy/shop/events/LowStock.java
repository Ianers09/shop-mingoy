package edu.cit.mingoy.shop.events;

public record LowStock(
        String productId,
        String productName,
        int remainingStock,
        int threshold
) {
}