package edu.cit.mingoy.supplier;

/**
 * Application-owned supplier order states.
 *
 * These are intentionally independent from LegacySupply's
 * numeric status codes.
 */
public enum SupplierOrderStatus {

    PENDING,
    ACCEPTED,
    PICKING,
    SHIPPED,
    DELIVERED,
    FAILED,
    CANCELLED,
    UNKNOWN
}