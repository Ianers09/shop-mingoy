import { useEffect, useState } from "react";
import "./App.css";

const API_URL = "http://localhost:8080/api";

function App() {
  const [inventory, setInventory] = useState([]);
  const [cart, setCart] = useState([]);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);

  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);

  const LOW_STOCK_THRESHOLD = 5;

  const loadInventory = async () => {
    try {
      const response = await fetch(`${API_URL}/inventory`);

      if (!response.ok) {
        throw new Error("Failed to load inventory");
      }

      const data = await response.json();
      setInventory(data);
    } catch (error) {
      console.error("Inventory error:", error);
    }
  };

  const loadOrders = async () => {
    try {
      const response = await fetch(`${API_URL}/orders`);

      if (!response.ok) {
        throw new Error("Failed to load orders");
      }

      const data = await response.json();
      setOrders(data);
    } catch (error) {
      console.error("Orders error:", error);
    }
  };

  const loadNotifications = async () => {
    try {
      const response = await fetch(`${API_URL}/notifications`);

      if (!response.ok) {
        throw new Error("Failed to load notifications");
      }

      const data = await response.json();
      setNotifications(data);
    } catch (error) {
      console.error("Notifications error:", error);
    }
  };

  const refreshData = async () => {
    await Promise.all([
      loadInventory(),
      loadOrders(),
      loadNotifications(),
    ]);
  };

  useEffect(() => {
    const loadInitialData = async () => {
      setLoadingData(true);
      await refreshData();
      setLoadingData(false);
    };

    loadInitialData();
  }, []);

  const addToCart = (productId) => {
    const product = inventory.find(
      (item) => item.productId === productId
    );

    if (!product) {
      return;
    }

    if (product.stock <= 0) {
      setResult({
        success: false,
        message: `${product.name} is out of stock.`,
      });
      return;
    }

    const existingItem = cart.find(
      (item) => item.productId === productId
    );

    if (existingItem) {
      if (existingItem.quantity >= product.stock) {
        setResult({
          success: false,
          message: `Only ${product.stock} unit(s) of ${product.name} are available.`,
        });
        return;
      }

      setCart(
        cart.map((item) =>
          item.productId === productId
            ? {
                ...item,
                quantity: item.quantity + 1,
              }
            : item
        )
      );

      return;
    }

    setCart([
      ...cart,
      {
        productId: product.productId,
        name: product.name,
        quantity: 1,
      },
    ]);
  };

  const updateCartQuantity = (productId, quantity) => {
    const product = inventory.find(
      (item) => item.productId === productId
    );

    if (!product) {
      return;
    }

    const newQuantity = Number(quantity);

    if (newQuantity <= 0) {
      removeFromCart(productId);
      return;
    }

    if (newQuantity > product.stock) {
      setResult({
        success: false,
        message: `Only ${product.stock} unit(s) of ${product.name} are available.`,
      });
      return;
    }

    setCart(
      cart.map((item) =>
        item.productId === productId
          ? {
              ...item,
              quantity: newQuantity,
            }
          : item
      )
    );
  };

  const removeFromCart = (productId) => {
    setCart(
      cart.filter((item) => item.productId !== productId)
    );
  };

  const clearCart = () => {
    setCart([]);
  };

  const handleSubmitOrder = async (e) => {
    e.preventDefault();

    if (cart.length === 0) {
      setResult({
        success: false,
        message: "Add at least one product to the cart.",
      });
      return;
    }

    setLoading(true);
    setResult(null);

    try {
      const response = await fetch(`${API_URL}/orders`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          items: cart.map((item) => ({
            productId: item.productId,
            quantity: Number(item.quantity),
          })),
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        setResult({
          success: false,
          message: data.reason || "Order request failed.",
          data,
        });
        return;
      }

      const success = data.status === "CONFIRMED";

      setResult({
        success,
        message:
          data.reason ||
          (success
            ? "Order confirmed."
            : "Order rejected."),
        data,
      });

      if (success) {
        setCart([]);
      }

      await refreshData();
    } catch (error) {
      console.error("Order error:", error);

      setResult({
        success: false,
        message:
          "Could not connect to the Spring Boot backend.",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = async (orderId) => {
    const confirmed = window.confirm(
      `Cancel order #${orderId}? The reserved inventory will be restocked.`
    );

    if (!confirmed) {
      return;
    }

    try {
      const response = await fetch(
        `${API_URL}/orders/${orderId}/cancel`,
        {
          method: "POST",
        }
      );

      const data = await response.json();

      if (!response.ok) {
        setResult({
          success: false,
          message:
            data.reason ||
            `Could not cancel order #${orderId}.`,
          data,
        });
        return;
      }

      setResult({
        success: true,
        message:
          data.reason ||
          `Order #${orderId} cancelled successfully.`,
        data,
      });

      await refreshData();
    } catch (error) {
      console.error("Cancel order error:", error);

      setResult({
        success: false,
        message:
          "Could not connect to the Spring Boot backend.",
      });
    }
  };

  const getProductName = (productId) => {
    const product = inventory.find(
      (item) => item.productId === productId
    );

    return product ? product.name : productId;
  };

  const formatDate = (dateValue) => {
    if (!dateValue) {
      return "";
    }

    const date = new Date(dateValue);

    if (Number.isNaN(date.getTime())) {
      return dateValue;
    }

    return date.toLocaleString();
  };

  if (loadingData) {
    return (
      <div className="app">
        <div className="container">
          <h1>Shop Order System</h1>
          <p className="subtitle">
            Modular Monolith Integration Lab
          </p>
          <p>Loading shop data...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <div className="container">
        <header className="page-header">
          <div>
            <h1>Shop Order System</h1>
            <p className="subtitle">
              Modular Monolith Integration Lab
            </p>
          </div>
        </header>

        {result && (
          <div
            className={`result ${
              result.success ? "success" : "error"
            }`}
          >
            <div className="result-content">
              <h2>
                {result.success
                  ? "Success"
                  : "Request Rejected"}
              </h2>

              <p>{result.message}</p>

              {result.data?.items && (
                <div className="result-items">
                  <strong>Line Items</strong>

                  {result.data.items.map((item, index) => (
                    <div
                      key={`${item.productId}-${index}`}
                      className="result-item"
                    >
                      <span>
                        {item.productId}
                      </span>
                      <span>
                        {item.outcome}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <button
              type="button"
              className="close-result"
              onClick={() => setResult(null)}
            >
              Close
            </button>
          </div>
        )}

        <section className="section">
          <div className="section-header">
            <div>
              <h2>Inventory</h2>
              <p>
                Current product stock
              </p>
            </div>
          </div>

          <div className="inventory-grid">
            {inventory.map((product) => (
              <div
                key={product.productId}
                className={`inventory-card ${
                  product.stock < LOW_STOCK_THRESHOLD
                    ? "low-stock"
                    : ""
                }`}
              >
                <div>
                  <span className="product-id">
                    {product.productId}
                  </span>

                  <h3>{product.name}</h3>
                </div>

                <div className="inventory-card-bottom">
                  <div>
                    <span className="stock-label">
                      Stock
                    </span>

                    <strong>{product.stock}</strong>
                  </div>

                  <button
                    type="button"
                    onClick={() =>
                      addToCart(product.productId)
                    }
                    disabled={product.stock <= 0}
                  >
                    {product.stock <= 0
                      ? "Out of Stock"
                      : "Add to Cart"}
                  </button>
                </div>

                {product.stock < LOW_STOCK_THRESHOLD && (
                  <div className="low-stock-warning">
                    Low stock — reorder needed
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>

        <section className="section">
          <div className="section-header">
            <div>
              <h2>Cart</h2>
              <p>
                {cart.length === 0
                  ? "No products added."
                  : `${cart.length} product${
                      cart.length === 1 ? "" : "s"
                    } in cart.`}
              </p>
            </div>

            {cart.length > 0 && (
              <button
                type="button"
                className="secondary-button"
                onClick={clearCart}
              >
                Clear Cart
              </button>
            )}
          </div>

          {cart.length === 0 ? (
            <div className="empty-state">
              Add products from the inventory above.
            </div>
          ) : (
            <form
              onSubmit={handleSubmitOrder}
              className="cart-form"
            >
              <div className="cart-list">
                {cart.map((item) => {
                  const product = inventory.find(
                    (productItem) =>
                      productItem.productId ===
                      item.productId
                  );

                  const maxStock = product
                    ? product.stock
                    : 0;

                  return (
                    <div
                      className="cart-item"
                      key={item.productId}
                    >
                      <div className="cart-item-info">
                        <span className="product-id">
                          {item.productId}
                        </span>

                        <strong>{item.name}</strong>
                      </div>

                      <div className="cart-item-actions">
                        <input
                          type="number"
                          min="1"
                          max={maxStock}
                          value={item.quantity}
                          onChange={(e) =>
                            updateCartQuantity(
                              item.productId,
                              e.target.value
                            )
                          }
                        />

                        <button
                          type="button"
                          className="remove-button"
                          onClick={() =>
                            removeFromCart(
                              item.productId
                            )
                          }
                        >
                          Remove
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>

              <button
                type="submit"
                className="place-order-button"
                disabled={loading}
              >
                {loading
                  ? "Processing..."
                  : "Place Order"}
              </button>
            </form>
          )}
        </section>

        <section className="section">
          <div className="section-header">
            <div>
              <h2>Order History</h2>
              <p>
                Previous orders and their current status
              </p>
            </div>
          </div>

          {orders.length === 0 ? (
            <div className="empty-state">
              No orders yet.
            </div>
          ) : (
            <div className="orders-list">
              {orders.map((order) => (
                <div
                  className="order-card"
                  key={order.orderId}
                >
                  <div className="order-header">
                    <div>
                      <strong>
                        Order #{order.orderId}
                      </strong>

                      <span>
                        {formatDate(order.createdAt)}
                      </span>
                    </div>

                    <span
                      className={`status-badge status-${String(
                        order.status
                      ).toLowerCase()}`}
                    >
                      {order.status}
                    </span>
                  </div>

                  <div className="order-items">
                    {(order.items || []).map(
                      (item, index) => (
                        <div
                          className="order-line"
                          key={`${order.orderId}-${item.productId}-${index}`}
                        >
                          <span>
                            {item.productId} -{" "}
                            {getProductName(
                              item.productId
                            )}
                          </span>

                          <strong>
                            × {item.quantity}
                          </strong>
                        </div>
                      )
                    )}
                  </div>

                  {order.reason && (
                    <p className="order-reason">
                      {order.reason}
                    </p>
                  )}

                  {order.status === "CONFIRMED" && (
                    <button
                      type="button"
                      className="cancel-button"
                      onClick={() =>
                        handleCancelOrder(
                          order.orderId
                        )
                      }
                    >
                      Cancel Order
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="section">
          <div className="section-header">
            <div>
              <h2>Notifications</h2>
              <p>
                Domain events received by the Notification
                module
              </p>
            </div>
          </div>

          {notifications.length === 0 ? (
            <div className="empty-state">
              No notifications yet.
            </div>
          ) : (
            <div className="notifications-list">
              {notifications.map((notification) => (
                <div
                  className="notification-card"
                  key={notification.notificationId}
                >
                  <div>
                    <strong>
                      {notification.message}
                    </strong>

                    <span>
                      {formatDate(
                        notification.createdAt
                      )}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

export default App;