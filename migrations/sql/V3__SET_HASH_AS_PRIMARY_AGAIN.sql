ALTER TABLE ATTRIBUTES DROP CONSTRAINT attributes_pkey;
ALTER TABLE ATTRIBUTES DROP COLUMN uuid;
ALTER TABLE ATTRIBUTES ADD PRIMARY KEY (hash);

ALTER TABLE COIN_TRANSFER DROP CONSTRAINT coin_transfer_pkey;
ALTER TABLE COIN_TRANSFER DROP COLUMN uuid;
ALTER TABLE COIN_TRANSFER ADD PRIMARY KEY (hash);

ALTER TABLE FEES DROP CONSTRAINT fees_pkey;
ALTER TABLE FEES DROP COLUMN uuid;
ALTER TABLE FEES ADD PRIMARY KEY (hash);

ALTER TABLE MARKER_SUPPLY DROP CONSTRAINT marker_supply_pkey;
ALTER TABLE MARKER_SUPPLY DROP COLUMN uuid;
ALTER TABLE MARKER_SUPPLY ADD PRIMARY KEY (hash);

ALTER TABLE MARKER_TRANSFER DROP CONSTRAINT marker_transfer_pkey;
ALTER TABLE MARKER_TRANSFER DROP COLUMN uuid;
ALTER TABLE MARKER_TRANSFER ADD PRIMARY KEY (hash);
