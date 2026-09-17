package edu.cit.mingoy.inventory;

import edu.cit.mingoy.shop.events.LowStock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId).orElse(null);
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }

    @Override
    @Transactional
    public InventoryItem reserve(String productId, int quantity) {

        if (quantity <= 0) {
            return null;
        }

        InventoryItem item =
                inventoryRepository.findById(productId).orElse(null);

        if (item == null) {
            return null;
        }

        if (item.getStock() < quantity) {
            return null;
        }

        item.setStock(item.getStock() - quantity);

        InventoryItem savedItem =
                inventoryRepository.save(item);

        if (savedItem.getStock() < LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(
                    new LowStock(
                            savedItem.getProductId(),
                            savedItem.getName(),
                            savedItem.getStock(),
                            LOW_STOCK_THRESHOLD
                    )
            );
        }

        return savedItem;
    }

    @Override
    @Transactional
    public InventoryItem restock(String productId, int quantity) {

        if (quantity <= 0) {
            return null;
        }

        InventoryItem item =
                inventoryRepository.findById(productId).orElse(null);

        if (item == null) {
            return null;
        }

        item.setStock(item.getStock() + quantity);

        return inventoryRepository.save(item);
    }
}