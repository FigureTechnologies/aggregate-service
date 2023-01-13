CREATE TABLE IF NOT EXISTS COIN_TRANSFER (
    id UUID PRIMARY KEY,
    event_type TEXT,
    block_height BIGINT,
    block_timestamp TIMESTAMPTZ,
    tx_hash TEXT,
    recipient TEXT,
    sender TEXT,
    amount TEXT,
    denom TEXT
);

CREATE TABLE IF NOT EXISTS FEES (
    id UUID PRIMARY KEY,
    tx_hash TEXT,
    block_height BIGINT,
    block_timestamp TIMESTAMPTZ,
    fee TEXT,
    fee_denom TEXT,
    sender TEXT
);

CREATE TABLE IF NOT EXISTS MARKER_TRANSFER(
    id UUID PRIMARY KEY,
    event_type TEXT,
    block_height BIGINT,
    block_timestamp TIMESTAMPTZ,
    amount TEXT,
    denom TEXT,
    administrator TEXT,
    to_address TEXT,
    from_address TEXT
);

CREATE TABLE IF NOT EXISTS MARKER_SUPPLY(
    id UUID PRIMARY KEY,
    event_type TEXT,
    block_height BIGINT,
    block_timestamp TIMESTAMPTZ,
    coins TEXT,
    denom TEXT,
    amount TEXT,
    administrator TEXT,
    to_address TEXT,
    from_address TEXT,
    metadata_base TEXT,
    metadata_description TEXT,
    metadata_display TEXT,
    metadata_denom_units TEXT,
    metadata_name TEXT,
    metadata_symbol TEXT
);

CREATE TABLE IF NOT EXISTS ATTRIBUTES(
    id UUID PRIMARY KEY,
    event_type TEXT,
    block_height BIGINT,
    block_timestamp TIMESTAMPTZ,
    name TEXT,
    value TEXT,
    type TEXT,
    account TEXT,
    owner TEXT
);


