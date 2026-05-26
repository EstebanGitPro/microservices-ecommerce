package com.ecomerce.product_service.model;

import org.springframework.data.mongodb.core.mapping.Document;

import java.lang.annotation.Documented;
import java.math.BigDecimal;


@Document( value = "product")
public class Product {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
}
