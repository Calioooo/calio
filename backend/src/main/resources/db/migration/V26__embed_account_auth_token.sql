ALTER TABLE accounts
    ADD COLUMN auth_token_hash VARCHAR(64);

ALTER TABLE accounts
    ADD COLUMN auth_token_revoked_at DATETIME(6);

ALTER TABLE accounts
    ADD COLUMN auth_token_last_used_at DATETIME(6);

UPDATE accounts account
SET auth_token_hash = (
        SELECT auth_token.token_hash
        FROM account_auth_tokens auth_token
        WHERE auth_token.account_id = account.id
    ),
    auth_token_revoked_at = (
        SELECT auth_token.revoked_at
        FROM account_auth_tokens auth_token
        WHERE auth_token.account_id = account.id
    ),
    auth_token_last_used_at = (
        SELECT auth_token.last_used_at
        FROM account_auth_tokens auth_token
        WHERE auth_token.account_id = account.id
    );

ALTER TABLE accounts
    ADD CONSTRAINT uk_accounts_auth_token_hash UNIQUE (auth_token_hash);

DROP TABLE account_auth_tokens;
