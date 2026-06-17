package com.ecomerce.product_service.dto;

public record ProductResponseDTO(
        String id,
        String name,
        String description,
        String pice
) {
}
