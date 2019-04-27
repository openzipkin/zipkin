CREATE TABLE IF NOT EXISTS zipkin.autocomplete_tags (
    key     text,
    value    text,
    PRIMARY KEY (key, value)
)
    WITH compaction = {'class': 'org.apache.cassandra.db.compaction.LeveledCompactionStrategy', 'unchecked_tombstone_compaction': 'true', 'tombstone_threshold': '0.2'}
    AND default_time_to_live =  259200
