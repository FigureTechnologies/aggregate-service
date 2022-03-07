CREATE TABLE IF NOT EXISTS attributes(
    hash TEXT NOT NULL PRIMARY KEY,
    event_type TEXT,
    block_height INT,
    block_timestamp TIMESTAMPTZ,
    name TEXT,
    value JSONB,
    type TEXT,
    account TEXT,
    owner TEXT
);

CREATE TABLE IF NOT EXISTS COIN_TRANSFER(
    hash TEXT NOT NULL PRIMARY KEY,
    event_type TEXT,
    block_height INT,
    block_timestamp TIMESTAMPTZ,
    recipient TEXT,
    sender TEXT,
    amount TEXT,
    denom TEXT
);

CREATE TABLE IF NOT EXISTS MARKER_SUPPLY(
    hash TEXT NOT NULL PRIMARY KEY,
    event_type TEXT,
    block_height INT,
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

CREATE TABLE IF NOT EXISTS MARKER_TRANSFER(
    hash TEXT NOT NULL PRIMARY KEY,
    event_type TEXT,
    block_height INT,
    block_timestamp TIMESTAMPTZ,
    amount INT,
    denom TEXT,
    administrator TEXT,
    to_address TEXT,
    from_address TEXT
);

CREATE TABLE IF NOT EXISTS NYCB_USDF_BALANCES(
    account TEXT NOT NULL PRIMARY KEY,
    date TEXT,
    balance INT,
    timestamp TIMESTAMPTZ,
    height INT
);

