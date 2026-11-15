CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(32) NOT NULL COMMENT '登录名',
    password_hash VARCHAR(128) NOT NULL COMMENT 'PBKDF2 密码散列',
    salt VARCHAR(64) NOT NULL COMMENT '随机盐',
    display_name VARCHAR(64) DEFAULT NULL COMMENT '显示名称',
    role VARCHAR(16) NOT NULL DEFAULT 'admin' COMMENT '角色：admin / operator',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统登录用户';

-- pond_config 增加增氧机额定功率。已有库需要补列；新库由调用方建表时一并包含。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，这里是幂等的条件 DDL。
SET @pond_config_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'pond_config'
);
SET @power_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'pond_config'
      AND column_name = 'aerator_power_kw'
);
SET @power_ddl = IF(@pond_config_exists > 0 AND @power_column_exists = 0,
    'ALTER TABLE pond_config ADD COLUMN aerator_power_kw DECIMAL(6,2) NOT NULL DEFAULT 1.50 COMMENT ''增氧机额定功率(kW)'' AFTER aerator_rate',
    'SELECT 1');
PREPARE pond_power_stmt FROM @power_ddl;
EXECUTE pond_power_stmt;
DEALLOCATE PREPARE pond_power_stmt;

-- 历史采样表：记录溶氧、水温和增氧机状态；新库直接创建，已有库只补索引。
CREATE TABLE IF NOT EXISTS do_sample (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    pond_id BIGINT NOT NULL COMMENT '鱼塘 ID',
    pond_time DATETIME NOT NULL COMMENT '鱼塘仿真时间',
    do_value DECIMAL(6, 2) DEFAULT NULL COMMENT '溶氧 mg/L',
    temperature DECIMAL(6, 2) DEFAULT NULL COMMENT '水温 ℃',
    ph_value DECIMAL(5, 2) DEFAULT NULL COMMENT 'pH',
    aerator_on TINYINT(1) NOT NULL DEFAULT 0 COMMENT '增氧机是否运行',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_do_sample_pond_time (pond_id, pond_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '溶氧采样历史';

-- 已有 do_sample 表时，上面的 CREATE 不会补索引，这里幂等补一次。
SET @do_sample_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'do_sample'
);
SET @do_sample_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'do_sample'
      AND index_name = 'idx_do_sample_pond_time'
);
SET @do_sample_index_ddl = IF(@do_sample_exists > 0 AND @do_sample_index_exists = 0,
    'ALTER TABLE do_sample ADD INDEX idx_do_sample_pond_time (pond_id, pond_time)',
    'SELECT 1');
PREPARE do_sample_index_stmt FROM @do_sample_index_ddl;
EXECUTE do_sample_index_stmt;
DEALLOCATE PREPARE do_sample_index_stmt;

-- 已有 do_sample 表补 pH 列。
SET @do_sample_ph_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'do_sample'
      AND column_name = 'ph_value'
);
SET @do_sample_ph_ddl = IF(@do_sample_exists > 0 AND @do_sample_ph_column_exists = 0,
    'ALTER TABLE do_sample ADD COLUMN ph_value DECIMAL(5,2) DEFAULT NULL COMMENT ''pH'' AFTER temperature',
    'SELECT 1');
PREPARE do_sample_ph_stmt FROM @do_sample_ph_ddl;
EXECUTE do_sample_ph_stmt;
DEALLOCATE PREPARE do_sample_ph_stmt;
