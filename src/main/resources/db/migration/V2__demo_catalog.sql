-- Demo catalog. SKU-SOLDOUT has no stock, to show the out-of-stock path.
INSERT INTO products (sku, name, unit_price, available) VALUES
    ('SKU-KEYBOARD', 'Mechanical keyboard',  89.00, 500),
    ('SKU-MOUSE',    'Wireless mouse',       39.00, 800),
    ('SKU-MONITOR',  '27" 4K monitor',      349.00, 120),
    ('SKU-LAPTOP',   'Developer laptop',   1890.00,  60),
    ('SKU-SERVER',   'Rack server',       12500.00,  10),
    ('SKU-SOLDOUT',  'Limited edition',     199.00,   0);
