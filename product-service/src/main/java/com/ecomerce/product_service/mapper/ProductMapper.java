package com.ecomerce.product_service.mapper;

import com.ecomerce.product_service.dto.ProductRequestDTO;
import com.ecomerce.product_service.dto.ProductResponseDTO;
import com.ecomerce.product_service.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "id", ignore = true)
    Product toProduct(ProductRequestDTO requestDTO);

    ProductResponseDTO toResponseDTO(Product product);
}
