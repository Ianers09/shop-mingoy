# LegacySupply Integration

## A. Baseline

The application integrates with LegacySupply through a dedicated supplier adapter.

LegacySupply base URL:

https://legacysupply.onrender.com/api/v1

The application uses XML requests and responses with:

Content-Type: application/xml

Authentication uses:

POST /auth/token

The application sends the LegacySupply client credentials to obtain a session token. The session token is stored and used only inside the supplier adapter.

The Order and Inventory modules do not directly communicate with LegacySupply.

## B. Discovered Supplier Behavior

### Product Mapping

The local product IDs are mapped to LegacySupply supplier SKUs:

| Local Product | Product Name | Supplier SKU | Pack Size |
|---|---|---|---:|
| P100 | Wireless Mouse | FFN-5102 | 6 |
| P200 | Mechanical Keyboard | FFN-2651 | 20 |
| P300 | USB-C Hub | FFN-4541 | 20 |

LegacySupply returned the unit of measure:

CS

The supplier quantity represents cases rather than individual local units.

The adapter therefore converts local units into supplier cases.

For example:

P100 requires 10 units.

Pack size = 6.

Cases required:

ceil(10 / 6) = 2 cases

Supplier quantity:

2 CS = 12 units

The local supplier order records both the requested local units and the resulting supplier cases.

### Session Handling

The session token is handled exclusively by LegacySupplyClient.

The application automatically authenticates when a session is unavailable and renews the session when LegacySupply reports an authentication/session problem.

The exact expiration duration was not established during manual discovery because the verification environment did not expose an expired-session event during the observed tests.

The implementation therefore does not hard-code an assumed session lifetime.

### Observed Errors

The following LegacySupply responses were observed during discovery:

E-AUTH-01

Credentials were rejected when invalid authentication credentials were supplied.

E-AUTH-03

A session was reported as not recognized during authentication/session testing.

E-SYS-50

A temporary supplier service outage was observed while submitting a purchase order.

The adapter treats temporary supplier failures as retryable and preserves the local supplier order as PENDING when the supplier cannot be reached successfully.

## C. ACL and Adapter Boundary

The LegacySupply-specific implementation is isolated inside the supplier module.

LegacySupply-specific concepts such as:

- XML authentication
- Session tokens
- Supplier SKUs
- LegacySupply status codes
- LegacySupply HTTP requests
- LegacySupply XML responses

are handled by LegacySupplyClient.

The public SupplierGateway exposes application-owned types:

SupplierGateway

SupplierOrderResult

SupplierOrderStatus

Order and Inventory do not depend directly on LegacySupplyClient.

The supplier credentials are loaded from environment configuration rather than being hard-coded into the application.

The supplier_orders table stores:

- product_id
- buyer_ref
- request_id
- po_number
- cases
- units
- status
- created_at
- updated_at

buyer_ref is unique.

request_id is unique.

At least three purchase orders were successfully created during LegacySupply discovery.

## D. Resilience

The LegacySupply client uses a maximum of three attempts for retryable supplier operations.

The configured connection and request timeouts are three seconds.

Retryable temporary failures use backoff between attempts.

The same X-Request-Id is preserved across retries.

The request ID is also persisted in supplier_orders.

If the application restarts after a supplier failure, the stored request ID is reused by the pending-order scheduler.

This prevents a new request ID from being generated for the same pending supplier order.

When LegacySupply is unavailable, the local supplier order remains PENDING.

The SupplierReorderScheduler periodically retries pending supplier orders.

The scheduled retry interval is 30 seconds.

## E. Delivery Tracking

The application periodically checks supplier orders that have a LegacySupply purchase-order number and have not reached a terminal state.

The tracking scheduler runs every 60 seconds.

LegacySupply status codes are mapped into application-owned statuses:

| LegacySupply Status | Application Status |
|---|---|
| 10 | ACCEPTED |
| 20 | PICKING |
| 30 | SHIPPED |
| 40 | DELIVERED |

Unexpected or unknown supplier status codes are mapped to:

UNKNOWN

Unknown statuses do not trigger inventory restocking.

When an order transitions to DELIVERED, the supplier module publishes:

SupplierOrderDelivered

The Inventory module listens for this application-owned event and restocks the delivered quantity.

The notification module is also separated from LegacySupply-specific HTTP and XML handling.

Supplier polling is performed only for open supplier orders so completed orders are not continuously queried.

## F. Idempotency

Every purchase-order request receives an application-generated request ID.

The request ID is sent using:

X-Request-Id

The same request ID is reused across retries.

The request ID is persisted in supplier_orders with a unique database constraint.

BuyerRef is also persisted with a unique database constraint.

If the application receives a request using an existing request ID or buyer reference, it reuses the existing supplier-order record instead of creating another local supplier order.

This provides application-level protection against duplicate submissions.

LegacySupply also provides request idempotency through X-Request-Id.

## G. Outage Recovery

A supplier order is created locally with PENDING status before the external supplier request is submitted.

If LegacySupply is temporarily unavailable, the local record remains PENDING.

The scheduled retry process searches for PENDING supplier orders and retries them using the original:

- product ID
- buyer reference
- request ID

This allows the application to recover from temporary supplier outages without generating a new request ID.

## H. Unexpected Supplier Behavior

Unexpected supplier status codes are mapped to UNKNOWN.

The application does not treat UNKNOWN as DELIVERED.

Therefore an unknown status cannot automatically trigger inventory restocking.

Unexpected supplier responses are handled inside the supplier adapter rather than being exposed to the Order or Inventory modules.

## I. Quota Considerations

The application avoids unnecessary supplier calls by:

- retrying only a limited number of times
- using backoff between retry attempts
- polling only open supplier orders
- stopping tracking after DELIVERED
- stopping tracking after CANCELLED
- avoiding repeated creation of supplier orders through request and buyer-reference uniqueness

The tracking scheduler runs once every 60 seconds.

The pending-order retry scheduler runs once every 30 seconds.

## J. Architecture Summary

React communicates with the Spring Boot application through HTTP.

The Spring Boot application contains the Order, Inventory, Notification, and Supplier modules.

Order and Inventory communicate through application-owned services and events.

The Supplier module acts as the anti-corruption layer around LegacySupply.

The dependency direction is:

React
→ Spring Boot API
→ Order / Inventory / Notification / Supplier

The external dependency is isolated as:

SupplierGateway
→ SupplierGatewayImpl
→ LegacySupplyClient
→ LegacySupply API

This prevents LegacySupply-specific XML, authentication, session handling, supplier SKU mappings, and HTTP behavior from leaking into the Order and Inventory modules.