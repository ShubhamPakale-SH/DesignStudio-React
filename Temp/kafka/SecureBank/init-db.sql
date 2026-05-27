CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(50) PRIMARY KEY,
    holder_name VARCHAR(100) NOT NULL,
    balance NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transaction_log (
    transaction_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO accounts (account_id, holder_name, balance) VALUES
    ('ACC1001', 'Alice Johnson', 5000.00),
    ('ACC1002', 'Bob Smith',     7500.00),
    ('ACC1003', 'Carol Davis',   2000.00)
ON CONFLICT (account_id) DO NOTHING;
