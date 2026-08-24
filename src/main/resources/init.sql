CREATE TABLE orders (
    id            BIGINT        PRIMARY KEY,
    customer_name VARCHAR(50)   NOT NULL,
    amount        DECIMAL(19,2) NOT NULL,
    status        VARCHAR(20)   NOT NULL
);

INSERT INTO orders VALUES (1, 'kim', 10000.00, 'NEW');
INSERT INTO orders VALUES (2, 'lee', 25000.50, 'PAID');
INSERT INTO orders VALUES (3, 'lee', 5000.00, 'NEW');
