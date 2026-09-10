package edu.cit.mingoy.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElse(null);
    }

    @Override
    @Transactional
    public InventoryItem reserve(String productId, int quantity) {

        InventoryItem item = inventoryRepository.findById(productId)
                .orElse(null);

        if (item == null) {
            return null;
        }

        if (quantity <= 0) {
            return null;
        }

        if (item.getStock() < quantity) {
            return null;
        }

        item.setStock(item.getStock() - quantity);

        return inventoryRepository.save(item);
    }
}