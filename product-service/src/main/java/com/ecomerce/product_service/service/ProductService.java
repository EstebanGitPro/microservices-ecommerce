package com.ecomerce.product_service.service;

import com.ecomerce.product_service.dto.ProductRequestDTO;
import com.ecomerce.product_service.dto.ProductResponseDTO;

import java.util.List;

public interface ProductService {
    ProductResponseDTO createProduct(ProductRequestDTO requestDTO);
    List<ProductResponseDTO> getAllProducts();
    ProductResponseDTO getProductById(String id);
    ProductResponseDTO updateProduct(String id, ProductRequestDTO productRequest);
    void deleteProduct(String id);
}
