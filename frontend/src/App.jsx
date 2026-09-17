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

  const getCartQuantity = (productId) => {
    const item = cart.find(
      (cartItem) => cartItem.productId === productId
    );

    return item ? item.quantity : 0;
  };

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

  const changeCartQuantity = (productId, amount) => {
    const currentQuantity = getCartQuantity(productId);

    updateCartQuantity(
      productId,
      currentQuantity + amount
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
          <div className="loading-screen">
            <div className="loading-spinner"></div>
            <h1>Shop Order System</h1>
            <p>Loading shop data...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <div className="container">

        <header className="page-header">
          <div>
            <div className="brand-mark">SHOP</div>
            <h1>Shop Order System</h1>
            <p className="subtitle">
              Modular Monolith Integration Lab
            </p>
          </div>

          <div className="header-cart">
            <span className="header-cart-label">
              Cart
            </span>

            <span className="header-cart-count">
              {cart.reduce(
                (total, item) => total + item.quantity,
                0
              )}
            </span>
          </div>
        </header>

        {result && (
          <div
            className={`result ${
              result.success ? "success" : "error"
            }`}
          >
            <div className="result-icon">
              {result.success ? "✓" : "!"}
            </div>

            <div className="result-content">
              <h2>
                {result.success
                  ? "Order Successful"
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
                        {item.productId} × {item.quantity}
                      </span>

                      <span className="result-outcome">
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
              aria-label="Close result"
            >
              ×
            </button>
          </div>
        )}

        <section className="section">
          <div className="section-header">
            <div>
              <span className="section-number">01</span>
              <div>
                <h2>Inventory</h2>
                <p>
                  Select products and adjust quantities.
                </p>
              </div>
            </div>
          </div>

          <div className="inventory-grid">
            {inventory.map((product) => {
              const cartQuantity =
                getCartQuantity(product.productId);

              const isLowStock =
                product.stock < LOW_STOCK_THRESHOLD;

              return (
                <div
                  key={product.productId}
                  className={`inventory-card ${
                    isLowStock ? "low-stock" : ""
                  }`}
                >
                  <div className="inventory-card-top">
                    <span className="product-id">
                      {product.productId}
                    </span>

                    {isLowStock && (
                      <span className="stock-status">
                        Low Stock
                      </span>
                    )}
                  </div>

                  <h3>{product.name}</h3>

                  <div className="inventory-details">
                    <div>
                      <span className="stock-label">
                        Available
                      </span>

                      <strong
                        className={
                          product.stock === 0
                            ? "out-stock"
                            : ""
                        }
                      >
                        {product.stock}
                      </strong>
                    </div>

                    <div className="selected-count">
                      <span className="stock-label">
                        In Cart
                      </span>

                      <strong>{cartQuantity}</strong>
                    </div>
                  </div>

                  <div className="quantity-control">
                    <button
                      type="button"
                      className="quantity-button"
                      onClick={() =>
                        changeCartQuantity(
                          product.productId,
                          -1
                        )
                      }
                      disabled={cartQuantity === 0}
                      aria-label={`Decrease ${product.name}`}
                    >
                      −
                    </button>

                    <span className="quantity-value">
                      {cartQuantity}
                    </span>

                    <button
                      type="button"
                      className="quantity-button"
                      onClick={() =>
                        addToCart(product.productId)
                      }
                      disabled={
                        product.stock <= 0 ||
                        cartQuantity >= product.stock
                      }
                      aria-label={`Increase ${product.name}`}
                    >
                      +
                    </button>
                  </div>

                  {product.stock <= 0 ? (
                    <div className="out-of-stock-label">
                      Out of Stock
                    </div>
                  ) : (
                    <div className="availability-label">
                      {product.stock} unit
                      {product.stock === 1 ? "" : "s"} available
                    </div>
                  )}

                  {isLowStock && (
                    <div className="low-stock-warning">
                      Low stock — reorder needed
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>

        <section className="section">
          <div className="section-header">
            <div>
              <span className="section-number">02</span>
              <div>
                <h2>Your Cart</h2>
                <p>
                  Review your items before placing the order.
                </p>
              </div>
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
              <div className="empty-state-icon">
                +
              </div>

              <strong>Your cart is empty</strong>

              <span>
                Add products from the inventory above.
              </span>
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

                        <span className="cart-availability">
                          {maxStock} available
                        </span>
                      </div>

                      <div className="cart-item-actions">
                        <div className="quantity-control cart-quantity">
                          <button
                            type="button"
                            className="quantity-button"
                            onClick={() =>
                              changeCartQuantity(
                                item.productId,
                                -1
                              )
                            }
                            aria-label={`Decrease ${item.name}`}
                          >
                            −
                          </button>

                          <span className="quantity-value">
                            {item.quantity}
                          </span>

                          <button
                            type="button"
                            className="quantity-button"
                            onClick={() =>
                              changeCartQuantity(
                                item.productId,
                                1
                              )
                            }
                            disabled={
                              item.quantity >= maxStock
                            }
                            aria-label={`Increase ${item.name}`}
                          >
                            +
                          </button>
                        </div>

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

              <div className="cart-footer">
                <div>
                  <span>Total Items</span>
                  <strong>
                    {cart.reduce(
                      (total, item) =>
                        total + item.quantity,
                      0
                    )}
                  </strong>
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
              </div>
            </form>
          )}
        </section>

        <section className="section">
          <div className="section-header">
            <div>
              <span className="section-number">03</span>
              <div>
                <h2>Order History</h2>
                <p>
                  Previous orders and their current status.
                </p>
              </div>
            </div>
          </div>

          {orders.length === 0 ? (
            <div className="empty-state">
              <strong>No orders yet</strong>
              <span>
                Your submitted orders will appear here.
              </span>
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
                            {item.productId} —{" "}
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
              <span className="section-number">04</span>
              <div>
                <h2>Notifications</h2>
                <p>
                  Events received by the Notification
                  module.
                </p>
              </div>
            </div>
          </div>

          {notifications.length === 0 ? (
            <div className="empty-state">
              <strong>No notifications yet</strong>
              <span>
                Order and inventory events will appear here.
              </span>
            </div>
          ) : (
            <div className="notifications-list">
              {notifications.map((notification) => (
                <div
                  className="notification-card"
                  key={notification.notificationId}
                >
                  <div className="notification-dot"></div>

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

        <footer className="page-footer">
          <span>SHOP ORDER SYSTEM</span>
          <span>Spring Boot + React + Supabase</span>
        </footer>

      </div>
    </div>
  );
}

export default App;