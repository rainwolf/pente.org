CHANGE MASTER TO
  MASTER_HOST='dsg_sql',
  MASTER_USER='replication_user',
  MASTER_PASSWORD='replication_password',
  MASTER_PORT=3306,
  MASTER_CONNECT_RETRY=10;