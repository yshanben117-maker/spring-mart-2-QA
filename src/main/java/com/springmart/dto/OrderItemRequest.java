package com.springmart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequest {
    @NotNull(message = "商品IDは必須です")
    private Long productId;
    
    @NotNull(message = "数量は必須です")
    @Min(value = 1, message = "数量は1以上である必要があります")
    private Integer quantity;

    public void setProductId(Long productId) { this.productId = productId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}

