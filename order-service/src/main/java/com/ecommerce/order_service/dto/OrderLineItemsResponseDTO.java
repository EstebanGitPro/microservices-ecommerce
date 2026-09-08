package com.ecommerce.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderLineItemsResponseDTO {
    private Long id; // AQUÍ SÍ VA EL ID
    private String sku;
    private BigDecimal price;
    private Integer quantity;
}