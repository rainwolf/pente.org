CHANGE MASTER TO
  MASTER_HOST='replica_auto_ssh',
  MASTER_USER='replication_user',
  MASTER_PASSWORD='replication_password',
  MASTER_PORT=3310,
  MASTER_CONNECT_RETRY=10;