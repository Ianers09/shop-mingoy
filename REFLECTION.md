# Lab 3 Reflection

## 1. How did you determine the supplier session behavior, and how does your application handle session expiration?

During integration testing, LegacySupply authentication was performed through the supplier adapter. The session token is kept inside LegacySupplyClient and is not exposed to the Order or Inventory modules.

The exact session lifetime was not measured because the verification environment did not produce an expired-session event during the observed testing period. Instead of assuming a fixed lifetime, the adapter handles authentication and session renewal when LegacySupply reports that the current session is unavailable or invalid.

This keeps session-specific behavior isolated from the rest of the application.

## 2. How did you determine the meaning of Qty and Uom, and how does your application convert local quantities?

The LegacySupply catalog showed supplier pack sizes and returned the unit of measure as CS.

The discovered mappings were:

| Product | Supplier SKU | Pack Size | Uom |
|---|---|---:|---|
| P100 | FFN-5102 | 6 | CS |
| P200 | FFN-2651 | 20 | CS |
| P300 | FFN-4541 | 20 | CS |

The supplier quantity therefore represents cases rather than individual local units.

The application converts local units into the required number of supplier cases using ceiling division.

For example, if 10 units of P100 are needed:

ceil(10 / 6) = 2 cases

The supplier receives 2 CS, which represents 12 supplier units.

The application stores both the requested local units and the calculated supplier quantity.

## 3. If LegacySupply were replaced with another supplier API using JSON instead of XML, which parts of the application would change?

The main changes would be inside the supplier module, especially LegacySupplyClient and the supplier-specific mapping and response handling.

The SupplierGateway interface would remain the application-facing abstraction.

Order and Inventory would not need to know whether the external supplier uses XML, JSON, different authentication, different status codes, or a different API protocol.

The replacement adapter would translate the new supplier's API into the application's existing SupplierOrderResult and SupplierOrderStatus types.

This demonstrates the purpose of the anti-corruption layer: external supplier-specific details remain isolated from the application's own domain modules.

# Lab 4 Reflection

## 1. How does the Tiangge feed remain safe across restarts and duplicate delivery?

The application stores the Tiangge feed cursor in the database and stores every processed event ID with its sequence number and type. After a restart, the application resumes from the stored cursor instead of starting from the beginning. Before processing an event, it checks whether the event ID has already been stored. This prevents the same event from being handled twice when Tiangge delivers it again.

## 2. How does the application handle Tiangge orders when stock is available or unavailable?

When all requested items are available, the application creates a local confirmed order, reserves the inventory, sends an ACCEPTED decision to Tiangge, and publishes the resulting stock. When the requested stock is not available, the application creates supplier replenishment orders for the missing units, records the local order as BACKORDERED, and sends a BACKORDERED decision to Tiangge. After the supplier delivery is received and the local inventory has enough stock, the backorder is fulfilled and Tiangge is resolved as ACCEPTED.

## 3. How are customer cancellations handled?

When an ORDER_CANCELLED event is received for an accepted Tiangge order, the application cancels the corresponding local order and restocks its reserved items. It then confirms the cancellation to Tiangge with restocked set to true and publishes the updated stock.
