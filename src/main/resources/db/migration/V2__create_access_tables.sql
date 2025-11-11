CREATE TABLE IF NOT EXISTS schema_version (
  version INT PRIMARY KEY,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_collections (
  player_uuid CHAR(36) NOT NULL,
  item_key    VARCHAR(128) NOT NULL,
  PRIMARY KEY (player_uuid, item_key)
);

CREATE TABLE IF NOT EXISTS server_collections (
  item_key VARCHAR(128) PRIMARY KEY
);

-- Table shops : conserver collect_first_setting pour compat lecture seule
ALTER TABLE shops ADD COLUMN IF NOT EXISTS collect_first_setting VARCHAR(10) DEFAULT 'NONE';
