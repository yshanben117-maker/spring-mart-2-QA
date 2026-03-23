-- 初期ユーザーデータ（パスワードは "password123" をBCryptでハッシュ化したもの）
-- 管理者ユーザー
INSERT INTO users (user_name, password, role, created_at) 
VALUES ('admin', '$2a$10$tPonV4A9FFiYn4RnyU0Z6ejJGglYg.NsNLm.MwhqeftToZkgT8weK', 'ROLE_ADMIN', NOW())
ON CONFLICT (user_name) DO NOTHING;

-- 一般ユーザー
INSERT INTO users (user_name, password, role, created_at) 
VALUES ('user1', '$2a$10$tPonV4A9FFiYn4RnyU0Z6ejJGglYg.NsNLm.MwhqeftToZkgT8weK', 'ROLE_USER', NOW())
ON CONFLICT (user_name) DO NOTHING;

-- 初期商品データ
INSERT INTO products (name, description, price, version, created_at) 
VALUES 
    ('Spring Boot入門', 'Spring Bootの基礎から実践まで学べる入門書', 3000, 0, NOW()),
    ('Docker実践ガイド', 'Dockerを使ったコンテナ開発の実践的なガイド', 3500, 0, NOW()),
    ('React開発集中講座', 'モダンフロントエンド開発の集中講座', 4000, 0, NOW())
ON CONFLICT DO NOTHING;

-- 初期在庫データ
INSERT INTO inventory (product_id, stock_quantity, updated_at)
SELECT id, 100, NOW()
FROM products
WHERE id IN (1, 2, 3)
ON CONFLICT (product_id) DO NOTHING;

