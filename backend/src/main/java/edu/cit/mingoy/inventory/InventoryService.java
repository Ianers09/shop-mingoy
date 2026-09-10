package edu.cit.mingoy.inventory;

public interface InventoryService {

    InventoryItem getItem(String productId);

    InventoryItem reserve(String productId, int quantity);
}