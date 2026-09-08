package com.ecommerce.order_service.service.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PutExchange;

public interface InvetoryClient {
    @PutExchange("/api/v1/inventory/reduce/{sku}")
    String reduceInventory(@PathVariable String sku, @RequestParam Integer quantity);
}
