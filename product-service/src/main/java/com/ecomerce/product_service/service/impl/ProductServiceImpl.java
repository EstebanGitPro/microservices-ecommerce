package com.ecomerce.product_service.service.impl;

import com.ecomerce.product_service.dto.ProductRequestDTO;
import com.ecomerce.product_service.dto.ProductResponseDTO;
import com.ecomerce.product_service.service.ProductService;

import java.util.List;

public class ProductServiceImpl implements ProductService {

    @Override
    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        return null;
    }

    @Override
    public List<ProductResponseDTO> getAllProducts() {
        return List.of();
    }
}
