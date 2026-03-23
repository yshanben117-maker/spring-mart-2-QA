package com.springmart.service;

import com.springmart.dto.OrderItemRequest;
import com.springmart.dto.OrderRequest;
import com.springmart.entity.Inventory;
import com.springmart.entity.Product;
import com.springmart.entity.User;
import com.springmart.repository.InventoryRepository;
import com.springmart.repository.OrderRepository;
import com.springmart.repository.ProductRepository;
import com.springmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceDeadlockTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long productAId;
    private Long productBId;

    @BeforeEach
    public void setup() {
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.deleteAll();
            inventoryRepository.deleteAll();
            productRepository.deleteAll();

            if (userRepository.findByUserName("user1").isEmpty()) {
                User user = new User();
                user.setUserName("user1");
                user.setPassword("password");
                user.setRole("ROLE_USER");
                userRepository.save(user);
            }

            Product productA = new Product();
            productA.setName("Product A");
            productA.setPrice(100);
            productA = productRepository.save(productA);
            productAId = productA.getId();

            Product productB = new Product();
            productB.setName("Product B");
            productB.setPrice(100);
            productB = productRepository.save(productB);
            productBId = productB.getId();

            Inventory invA = new Inventory();
            invA.setProduct(productA);
            invA.setStockQuantity(100);
            inventoryRepository.save(invA);

            Inventory invB = new Inventory();
            invB.setProduct(productB);
            invB.setStockQuantity(100);
            inventoryRepository.save(invB);
        });
    }

    @Test
    public void testDeadlock() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // We use a latch to start threads simultaneously, but to ensure they overlap at the SELECT FOR UPDATE,
        // it may be slightly race-condition dependent. A small thread.sleep in the service would make it 100% reliable,
        // but this often works.
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        executor.execute(() -> {
            try {
                latch.await();
                OrderRequest request = new OrderRequest();
                OrderItemRequest item1 = new OrderItemRequest(); item1.setProductId(productAId); item1.setQuantity(1);
                OrderItemRequest item2 = new OrderItemRequest(); item2.setProductId(productBId); item2.setQuantity(1);
                request.setItems(Arrays.asList(item1, item2));
                orderService.createOrder(request);
            } catch (Exception e) {
                exceptionRef.set(e);
            } finally {
                done.countDown();
            }
        });

        executor.execute(() -> {
            try {
                latch.await();
                OrderRequest request = new OrderRequest();
                OrderItemRequest item1 = new OrderItemRequest(); item1.setProductId(productBId); item1.setQuantity(1);
                OrderItemRequest item2 = new OrderItemRequest(); item2.setProductId(productAId); item2.setQuantity(1);
                request.setItems(Arrays.asList(item1, item2));
                orderService.createOrder(request);
            } catch (Exception e) {
                exceptionRef.set(e);
            } finally {
                done.countDown();
            }
        });

        latch.countDown();
        done.await();
        executor.shutdown();

        Exception e = exceptionRef.get();
        System.out.println("Exception caught from parallel execution: " + (e != null ? e.getClass().getName() : "None"));
        if (e != null) {
            e.printStackTrace();
        }
        
        assertNotNull(e, "例外が発生していません。デッドロック（またはロックタイムアウト）になるはずです。");
    }
}
