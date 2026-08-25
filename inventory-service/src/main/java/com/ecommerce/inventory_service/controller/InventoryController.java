package com.ecommerce.inventory_service.controller;

import com.ecommerce.inventory_service.dto.InventoryRequestDTO;
import com.ecommerce.inventory_service.dto.InventoryResponseDTO;
import com.ecommerce.inventory_service.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory") // Igualamos el versionado v1
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // --- Endpoint Especial (Lógica de Negocio) ---
    @GetMapping("/{sku}")
    @ResponseStatus(HttpStatus.OK)
    public boolean isInStock(@PathVariable("sku") String sku, @RequestParam("quantity") Integer quantity) {
        return inventoryService.isInStock(sku, quantity);
    }

    // --- Endpoints CRUD (Estilo ProductController) ---

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponseDTO createInventory(@RequestBody @Valid InventoryRequestDTO inventoryRequest) {
        return inventoryService.createInventory(inventoryRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponseDTO> getAllInventory() {
        return inventoryService.getAllInventory();
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public InventoryResponseDTO updateInventory(@PathVariable Long id,
                                                @RequestBody @Valid InventoryRequestDTO inventoryRequest) {
        return inventoryService.updateInventory(id, inventoryRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInventory(@PathVariable Long id) {
        inventoryService.deleteInventory(id);
    }
}
