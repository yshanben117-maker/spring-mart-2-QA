package com.springmart.service;

import com.springmart.dto.OrderItemRequest;
import com.springmart.dto.OrderRequest;
import com.springmart.dto.OrderResponse;
import com.springmart.entity.*;
import com.springmart.exception.OutOfStockException;
import com.springmart.repository.InventoryRepository;
import com.springmart.repository.OrderDetailRepository;
import com.springmart.repository.OrderRepository;
import com.springmart.repository.ProductRepository;
import com.springmart.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, OrderDetailRepository orderDetailRepository,
                       ProductRepository productRepository, InventoryRepository inventoryRepository,
                       UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        String username;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            username = authentication.getName();
        } else {
            username = "user1";
        }

        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("ユーザーが見つかりません: " + username));

        Order order = new Order();
        order.setUser(user);
        order.setStatus("COMPLETED");

        List<OrderDetail> orderDetails = new ArrayList<>();
        int totalPrice = 0;

        // 各商品について在庫確認と引き当て（悲観的ロックで排他制御）
        for (OrderItemRequest itemRequest : request.getItems()) {
            Long productId = itemRequest.getProductId();
            Integer quantity = itemRequest.getQuantity();

            // findByIdForUpdate で SELECT FOR UPDATE（悲観的ロック）を取得
            Inventory inventory = inventoryRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new RuntimeException("商品が見つかりません: " + productId));

            // 在庫が足りない場合は例外を投げる → @Transactional によって全てロールバック（DB保存キャンセル）
            if (inventory.getStockQuantity() < quantity) {
                Product p = productRepository.findById(productId)
                        .orElseThrow(() -> new RuntimeException("商品が見つかりません: " + productId));
                throw new OutOfStockException(
                        "商品 '" + p.getName() + "' (ID: " + productId + ") の在庫が不足しています。" +
                        "（在庫: " + inventory.getStockQuantity() + ", 注文数: " + quantity + "）"
                );
            }

            // 在庫を減算して保存
            inventory.setStockQuantity(inventory.getStockQuantity() - quantity);
            inventoryRepository.save(inventory);

            // 商品情報取得
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("商品が見つかりません: " + productId));

            // 注文明細作成
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrder(order);
            orderDetail.setProduct(product);
            orderDetail.setQuantity(quantity);
            orderDetail.setPriceAtOrder(product.getPrice());
            orderDetails.add(orderDetail);

            totalPrice += product.getPrice() * quantity;
        }

        order.setTotalPrice(totalPrice);
        order.setOrderDetails(orderDetails);

        order = orderRepository.save(order);

        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalPrice());
    }
}

