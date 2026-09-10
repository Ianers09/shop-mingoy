import { useState } from "react";
import "./App.css";

function App() {
  const [productId, setProductId] = useState("P100");
  const [quantity, setQuantity] = useState(1);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    setLoading(true);
    setResult(null);

    try {
      const response = await fetch("http://localhost:8080/api/orders", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          productId,
          quantity: Number(quantity),
        }),
      });

      const data = await response.json();

      setResult({
        success: data.status === "CONFIRMED",
        data,
      });
    } catch (error) {
      setResult({
        success: false,
        data: {
          status: "ERROR",
          reason: "Could not connect to the Spring Boot backend.",
        },
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app">
      <div className="container">
        <h1>Shop Order System</h1>

        <p className="subtitle">
          Modular Monolith Integration Lab
        </p>

        <form onSubmit={handleSubmit} className="order-form">
          <label htmlFor="product">Product</label>

          <select
            id="product"
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
          >
            <option value="P100">
              P100 - Wireless Mouse
            </option>

            <option value="P200">
              P200 - Mechanical Keyboard
            </option>

            <option value="P300">
              P300 - USB-C Hub
            </option>
          </select>

          <label htmlFor="quantity">Quantity</label>

          <input
            id="quantity"
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />

          <button type="submit" disabled={loading}>
            {loading ? "Processing..." : "Place Order"}
          </button>
        </form>

        {result && (
          <div
            className={`result ${
              result.success ? "success" : "error"
            }`}
          >
            <div className="status-icon">
              {result.success ? "✓" : "!"}
            </div>

            <div className="result-content">
              <h2>
                {result.success
                  ? "Order Confirmed"
                  : "Order Rejected"}
              </h2>

              <p className="status-text">
                <strong>Status:</strong>{" "}
                {result.data.status}
              </p>

              <p>
                <strong>Reason:</strong>{" "}
                {result.data.reason}
              </p>

              {result.data.inventory && (
                <div className="inventory-info">
                  <p>
                    <strong>Product:</strong>{" "}
                    {result.data.inventory.name}
                  </p>

                  <p>
                    <strong>Remaining Stock:</strong>{" "}
                    {result.data.inventory.stock}
                  </p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default App;