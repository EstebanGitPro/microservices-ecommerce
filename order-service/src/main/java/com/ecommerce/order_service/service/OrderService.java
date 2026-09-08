package com.ecommerce.order_service.service;

import com.ecommerce.order_service.dto.OrderRequestDTO;
import com.ecommerce.order_service.dto.OrderResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

public interface OrderService {
    OrderResponseDTO placeOrder(OrderRequestDTO orderRequest); // Create
    List<OrderResponseDTO> getAllOrders();                  // Read All
    OrderResponseDTO getOrderById(Long id);                 // Read One
    void deleteOrder(Long id);                           // Delete
}