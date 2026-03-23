-- ユーザーテーブル
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    user_name VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 商品テーブル
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price INTEGER NOT NULL,
    version INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 在庫テーブル
CREATE TABLE IF NOT EXISTS inventory (
    product_id BIGINT PRIMARY KEY,
    stock_quantity INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- 注文テーブル
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_price INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    ordered_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 注文明細テーブル
CREATE TABLE IF NOT EXISTS order_details (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    price_at_order INTEGER NOT NULL,
    CONSTRAINT fk_order_details_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_details_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- インデックス作成
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_order_details_order_id ON order_details(order_id);
CREATE INDEX IF NOT EXISTS idx_order_details_product_id ON order_details(product_id);

