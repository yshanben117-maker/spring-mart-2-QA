package com.springmart.service;

import com.springmart.dto.ProductRequest;
import com.springmart.dto.ProductResponse;
import com.springmart.entity.Inventory;
import com.springmart.entity.Product;
import com.springmart.repository.InventoryRepository;
import com.springmart.repository.ProductRepository;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(p -> new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice()))
                .collect(Collectors.toList());
    }

    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品が見つかりません: " + id));
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice());
    }

    public ProductResponse createProduct(ProductRequest request) {

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product = productRepository.save(product);

        // 在庫テーブルに初期在庫数を登録
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setStockQuantity(request.getInitialStock());
        inventoryRepository.save(inventory);

        return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice());
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        // ①対象の商品をDBから探す。なければエラーメッセージを出す。
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品が見つかりません:" + id));

        // ②リクエストの内容で商品情報を上書きする
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        // ③更新した商品をDBに保存する
        product = productRepository.save(product);

        // ④更新後の商品情報を返す
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice());
    }

    @Transactional
    public void deleteProduct(Long id) {
        // ①対象の商品を探す
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品が見つかりません: " + id));

        // ②DBから削除する
        productRepository.delete(product);
    }
}

