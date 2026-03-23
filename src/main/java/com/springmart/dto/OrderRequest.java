package com.springmart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequest {
    @Valid
    @NotEmpty(message = "注文商品リストは必須です")
    private List<OrderItemRequest> items;

    public void setItems(List<OrderItemRequest> items) { this.items = items; }
}

