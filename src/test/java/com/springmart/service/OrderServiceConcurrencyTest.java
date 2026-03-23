package com.springmart.service;

import com.springmart.dto.OrderItemRequest;
import com.springmart.dto.OrderRequest;
import com.springmart.entity.Inventory;
import com.springmart.entity.Product;
import com.springmart.entity.User;
import com.springmart.exception.OutOfStockException;
import com.springmart.repository.InventoryRepository;
import com.springmart.repository.OrderRepository;
import com.springmart.repository.ProductRepository;
import com.springmart.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Long testProductId;

    @BeforeEach
    public void setup() {
        transactionTemplate.executeWithoutResult(status -> {
            // テストデータが残っている可能性があるので初期化
            orderRepository.deleteAll();
            inventoryRepository.deleteAll();
            productRepository.deleteAll();

            // -------------------------------------------------------------
            // OrderServiceの内部で user1 というユーザーを固定で取得しているため、
            // DBに存在しない場合は作成しておく
            // -------------------------------------------------------------
            if (userRepository.findByUserName("user1").isEmpty()) {
                User user = new User();
                user.setUserName("user1");
                user.setPassword("password");
                user.setRole("ROLE_USER");
                userRepository.save(user);
            }

            // テスト用商品の作成
            Product product = new Product();
            product.setName("春の限定スニーカー");
            product.setDescription("人気商品につき数量限定！");
            product.setPrice(15000);
            product = productRepository.save(product);
            testProductId = product.getId();

            // インベントリ(在庫)作成：初期在庫を「1個」に設定
            Inventory inventory = new Inventory();
            inventory.setProduct(product);
            inventory.setStockQuantity(1);
            inventoryRepository.save(inventory);
        });
    }

    @AfterEach
    public void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            // 後続のテストに影響を与えないようクリーンアップ
            orderRepository.deleteAll();
            inventoryRepository.deleteAll();
            productRepository.deleteAll();
        });
    }

    @Test
    @DisplayName("複数スレッドからの同時注文処理時、排他制御によって在庫がマイナスにならないこと")
    public void testConcurrentOrders() throws InterruptedException {
        int threadCount = 10; // 10人が同時にアクセスするシミュレーション
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // 10スレッドを準備
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    // 全スレッドが揃うまで待機（よーいドンの合図待ち）
                    latch.await();

                    // 注文リクエストオブジェクトを生成
                    OrderRequest request = new OrderRequest();
                    OrderItemRequest item = new OrderItemRequest();
                    item.setProductId(testProductId);
                    item.setQuantity(1); // 1個の注文
                    request.setItems(Collections.singletonList(item));

                    // 注文処理を実行
                    orderService.createOrder(request);
                    successCount.incrementAndGet();
                } catch (OutOfStockException e) {
                    // 排他制御の結果、在庫不足エラーになればこちらに入る
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // スレッドの準備完了後、一斉に処理開始
        latch.countDown();
        
        // 全スレッドの完了を待つ
        doneLatch.await();
        executorService.shutdown();

        // --- アサーション（期待値の検証） ---

        // 1. 10件のリクエスト中、成功したのは「1件」だけであること
        assertEquals(1, successCount.get(), "成功した注文は1件のみであるべき");
        
        // 2. 残りの「9件」は在庫不足（排他エラー）として失敗していること
        assertEquals(9, failCount.get(), "残り9件は排他エラーで失敗すべき");

        // 3. DB上の最終的な在庫数が「0」になっていること（マイナスでないこと）
        Inventory finalInventory = inventoryRepository.findById(testProductId).orElseThrow();
        assertEquals(0, finalInventory.getStockQuantity(), "最終在庫数は0であるべき");
        
        // 4. DB上に作成された注文レコードが「1件」のみであること
        long orderCount = orderRepository.count();
        assertEquals(1, orderCount, "全体で登録された注文数は1件のみであるべき");
    }
}
