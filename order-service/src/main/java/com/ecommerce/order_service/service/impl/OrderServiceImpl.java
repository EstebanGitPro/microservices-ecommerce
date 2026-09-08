package com.ecommerce.order_service.service.impl;

import com.ecommerce.order_service.dto.OrderRequestDTO;
import com.ecommerce.order_service.dto.OrderResponseDTO;
import com.ecommerce.order_service.exception.ResourceNotFoundException;
import com.ecommerce.order_service.mapper.OrderMapper;
import com.ecommerce.order_service.model.Order;
import com.ecommerce.order_service.repository.OrderRepository;
import com.ecommerce.order_service.service.OrderService;
import com.ecommerce.order_service.service.client.InvetoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;


import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    //    private final WebClient.Builder webClientBuilder;
    private final InvetoryClient inventoryClient;

    @Override
    @Transactional
    public OrderResponseDTO placeOrder(OrderRequestDTO orderRequest) {

        log.info("Colocando nuevo pedido");

        Order order = orderMapper.toOrder(orderRequest);

        for(var item : order.getOrderLineItemsList()){
            String sku = item.getSku();
            Integer quantity = item.getQuantity();

            try {
//               webClientBuilder.build().put()
//                       .uri("http://localhost:8082/api/v1/inventory/reduce/" + sku,
//                               uriBuilder -> uriBuilder.queryParam("quantity", quantity).build())
//                       .retrieve()
//                       .bodyToMono(String.class)
//                       .block();
                inventoryClient.reduceInventory(sku, quantity);

            } catch (WebClientResponseException e) {
                log.error("Inventory service rejected SKU {}: {} - {}",
                        sku, e.getStatusCode(), e.getResponseBodyAsString());
                throw new IllegalArgumentException(
                        "No se pudo procesar la orden: stock insuficiente para el SKU " + sku);
            } catch (WebClientRequestException e) {
                log.error("Inventory service unreachable while reducing SKU {}: {}", sku, e.getMessage());
                throw new IllegalStateException(
                        "No se pudo contactar al servicio de inventario", e);
            }


        }

        order.setOrderNumber(UUID.randomUUID().toString());

        Order savedOrder = orderRepository.save(order);

        log.info("Orden guardada con éxito. ID: {}", savedOrder.getId());

        return orderMapper.toOrderResponseDTO(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toOrderResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Orden", "id", id)
                );

        return orderMapper.toOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {

        if(!orderRepository.existsById(id)){
            throw new ResourceNotFoundException("Orden", "id", id);
        }

        orderRepository.deleteById(id);
        log.info("Orden eliminada. ID: {}", id);
    }


}