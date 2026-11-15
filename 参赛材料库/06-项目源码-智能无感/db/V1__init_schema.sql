-- ============================================================================
-- AI 节律守护 · MySQL 建表脚本
-- ----------------------------------------------------------------------------
-- 从原项目 Drizzle schema（PostgreSQL）转换而来
--
-- 类型转换对照：
--   serial          -> BIGINT NOT NULL AUTO_INCREMENT
--   varchar(n)      -> VARCHAR(n)
--   text            -> TEXT
--   boolean         -> TINYINT(1)
--   timestamp(tz)   -> DATETIME(3)
--   date            -> DATE
--   numeric(p,s)    -> DECIMAL(p+2,s)   -- 留出整数位余量
--   jsonb           -> JSON
--   gen_random_uuid -> 应用层生成（MySQL 8 用 UUID()）
--
-- ★ 字符集统一 utf8mb4，避免中文乱码
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `znwg`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `znwg`;

-- ============================================================================
-- 1. 用户表
-- ============================================================================
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id`              VARCHAR(36)   NOT NULL COMMENT '用户ID（UUID）',
  `phone`           VARCHAR(20)   NOT NULL COMMENT '手机号',
  `name`            VARCHAR(128)  DEFAULT NULL COMMENT '姓名',
  `avatar`          VARCHAR(512)  DEFAULT NULL COMMENT '头像URL',
  `role`            VARCHAR(20)   NOT NULL DEFAULT 'elder' COMMENT '角色：elder 长辈 / family 家属',
  `birth_date`      DATE          DEFAULT NULL COMMENT '出生日期',
  `gender`          VARCHAR(10)   DEFAULT NULL COMMENT '性别',
  `address`         TEXT          COMMENT '地址',
  `emergency_phone` VARCHAR(20)   DEFAULT NULL COMMENT '紧急联系电话',
  `is_active`       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
  `is_admin`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否管理员（可登录后台）',
  `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)   DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_phone` (`phone`),
  KEY `idx_users_role` (`role`),
  KEY `idx_users_admin` (`is_admin`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户（长辈 / 家属）';

-- ============================================================================
-- 2. 打卡记录表
-- ============================================================================
DROP TABLE IF EXISTS `checkin_records`;
CREATE TABLE `checkin_records` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`     VARCHAR(36)   NOT NULL COMMENT '用户ID',
  `date`        DATE          NOT NULL COMMENT '日期',
  `wake_time`   VARCHAR(10)   DEFAULT NULL COMMENT '起床时间 HH:mm',
  `sleep_time`  VARCHAR(10)   DEFAULT NULL COMMENT '入睡时间 HH:mm',
  `wake_score`  INT           DEFAULT 0 COMMENT '起床评分',
  `sleep_score` INT           DEFAULT 0 COMMENT '入睡评分',
  `created_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)   DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkin_user_date` (`user_id`, `date`),
  KEY `idx_checkin_user` (`user_id`),
  KEY `idx_checkin_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作息打卡记录';

-- ============================================================================
-- 3. 健康检查表（探活）
-- ============================================================================
DROP TABLE IF EXISTS `health_check`;
CREATE TABLE `health_check` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `updated_at` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康检查';

-- ============================================================================
-- 4. 设备表
-- ============================================================================
DROP TABLE IF EXISTS `devices`;
CREATE TABLE `devices` (
  `id`          VARCHAR(36)   NOT NULL COMMENT '设备ID',
  `user_id`     VARCHAR(36)   NOT NULL COMMENT '所属用户',
  `device_name` VARCHAR(100)  NOT NULL COMMENT '设备名称',
  `device_type` VARCHAR(30)   NOT NULL COMMENT '设备类型：watch/band/sensor/...',
  `device_mac`  VARCHAR(50)   DEFAULT NULL COMMENT '设备MAC地址',
  `status`      VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '状态：active/offline/unbound',
  `last_sync`   DATETIME(3)   DEFAULT NULL COMMENT '最后同步时间',
  `metadata`    JSON          DEFAULT NULL COMMENT '扩展信息',
  `created_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)   DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_devices_user` (`user_id`),
  KEY `idx_devices_type` (`device_type`),
  KEY `idx_devices_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绑定的智能设备';

-- ============================================================================
-- 5. 告警表
-- ============================================================================
DROP TABLE IF EXISTS `alerts`;
CREATE TABLE `alerts` (
  `id`          VARCHAR(36)   NOT NULL COMMENT '告警ID',
  `user_id`     VARCHAR(36)   NOT NULL COMMENT '用户ID',
  `alert_type`  VARCHAR(30)   NOT NULL COMMENT '告警类型：fall/sos/abnormal/...',
  `severity`    VARCHAR(20)   NOT NULL DEFAULT 'medium' COMMENT '严重程度：low/medium/high/critical',
  `title`       VARCHAR(200)  NOT NULL COMMENT '标题',
  `description` TEXT          COMMENT '详细描述',
  `occurred_at` DATETIME(3)   NOT NULL COMMENT '发生时间',
  `handled`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已处理',
  `handled_by`  VARCHAR(36)   DEFAULT NULL COMMENT '处理人ID',
  `handled_at`  DATETIME(3)   DEFAULT NULL COMMENT '处理时间',
  `notes`       TEXT          COMMENT '处理备注',
  `created_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_alerts_user` (`user_id`),
  KEY `idx_alerts_handled` (`handled`),
  KEY `idx_alerts_type` (`alert_type`),
  KEY `idx_alerts_occurred` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康告警';

-- ============================================================================
-- 6. 饮食记录表
-- ============================================================================
DROP TABLE IF EXISTS `diet_records`;
CREATE TABLE `diet_records` (
  `id`         VARCHAR(36)  NOT NULL COMMENT '记录ID',
  `user_id`    VARCHAR(36)  NOT NULL COMMENT '用户ID',
  `date`       DATE         NOT NULL COMMENT '日期',
  `meal_type`  VARCHAR(20)  NOT NULL COMMENT '餐次：breakfast/lunch/dinner/snack',
  `meal_time`  VARCHAR(10)  NOT NULL COMMENT '用餐时间 HH:mm',
  `foods`      JSON         NOT NULL COMMENT '食物列表',
  `calories`   INT          DEFAULT 0 COMMENT '热量 kcal',
  `protein`    INT          DEFAULT 0 COMMENT '蛋白质 g',
  `carbs`      INT          DEFAULT 0 COMMENT '碳水 g',
  `fat`        INT          DEFAULT 0 COMMENT '脂肪 g',
  `notes`      TEXT         COMMENT '备注',
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_diet_user_date` (`user_id`, `date`),
  KEY `idx_diet_meal` (`meal_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食记录';

-- ============================================================================
-- 7. 长辈健康档案表
-- ============================================================================
DROP TABLE IF EXISTS `elder_info`;
CREATE TABLE `elder_info` (
  `id`                VARCHAR(36)   NOT NULL COMMENT '档案ID',
  `user_id`           VARCHAR(36)   NOT NULL COMMENT '用户ID',
  `blood_type`        VARCHAR(10)   DEFAULT NULL COMMENT '血型',
  `height`            DECIMAL(7,2)  DEFAULT NULL COMMENT '身高 cm',
  `weight`            DECIMAL(7,2)  DEFAULT NULL COMMENT '体重 kg',
  `chronic_diseases`  JSON          DEFAULT NULL COMMENT '慢性病列表',
  `allergies`         JSON          DEFAULT NULL COMMENT '过敏源列表',
  `medications`       JSON          DEFAULT NULL COMMENT '长期用药列表',
  `wake_time_target`  VARCHAR(10)   DEFAULT NULL COMMENT '目标起床时间',
  `sleep_time_target` VARCHAR(10)   DEFAULT NULL COMMENT '目标入睡时间',
  `exercise_frequency` VARCHAR(20)  DEFAULT NULL COMMENT '运动频率',
  `diet_preference`   JSON          DEFAULT NULL COMMENT '饮食偏好',
  `smoking`           TINYINT(1)    DEFAULT 0 COMMENT '是否吸烟',
  `drinking`          TINYINT(1)    DEFAULT 0 COMMENT '是否饮酒',
  `created_at`        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`        DATETIME(3)   DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_elder_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长辈健康档案';

-- ============================================================================
-- 8. 用药提醒表
-- ============================================================================
DROP TABLE IF EXISTS `medicine_reminders`;
CREATE TABLE `medicine_reminders` (
  `id`            VARCHAR(36)   NOT NULL COMMENT '提醒ID',
  `user_id`       VARCHAR(36)   NOT NULL COMMENT '用户ID',
  `medicine_name` VARCHAR(128)  NOT NULL COMMENT '药品名称',
  `dosage`        VARCHAR(50)   NOT NULL COMMENT '剂量',
  `frequency`     VARCHAR(20)   NOT NULL COMMENT '频次：daily/twice/three_times/...',
  `times`         JSON          NOT NULL COMMENT '提醒时间点列表',
  `start_date`    DATE          NOT NULL COMMENT '开始日期',
  `end_date`      DATE          DEFAULT NULL COMMENT '结束日期（空=长期）',
  `notes`         TEXT          COMMENT '备注',
  `is_active`     TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at`    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)   DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_med_reminder_user` (`user_id`),
  KEY `idx_med_reminder_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用药提醒';

-- ============================================================================
-- 9. 监测数据表
-- ============================================================================
DROP TABLE IF EXISTS `monitor_data`;
CREATE TABLE `monitor_data` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`     VARCHAR(36)   NOT NULL COMMENT '用户ID',
  `device_id`   VARCHAR(36)   DEFAULT NULL COMMENT '来源设备',
  `data_type`   VARCHAR(30)   NOT NULL COMMENT '数据类型：heart_rate/step/sleep/spo2/...',
  `value`       DECIMAL(12,4) DEFAULT NULL COMMENT '数值',
  `unit`        VARCHAR(20)   DEFAULT NULL COMMENT '单位',
  `recorded_at` DATETIME(3)   NOT NULL COMMENT '采集时间',
  `metadata`    JSON          DEFAULT NULL COMMENT '扩展信息',
  `created_at`  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_monitor_user_type` (`user_id`, `data_type`),
  KEY `idx_monitor_recorded` (`recorded_at`),
  KEY `idx_monitor_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康监测数据';

-- ============================================================================
-- 10. 运动记录表
-- ============================================================================
DROP TABLE IF EXISTS `exercise_records`;
CREATE TABLE `exercise_records` (
  `id`            VARCHAR(36)   NOT NULL COMMENT '记录ID',
  `user_id`       VARCHAR(36)   NOT NULL COMMENT '用户ID',
  `date`          DATE          NOT NULL COMMENT '日期',
  `exercise_type` VARCHAR(50)   NOT NULL COMMENT '运动类型：walk/run/taichi/...',
  `duration`      INT           NOT NULL COMMENT '时长（分钟）',
  `steps`         INT           DEFAULT 0 COMMENT '步数',
  `calories`      INT           DEFAULT 0 COMMENT '消耗热量 kcal',
  `distance`      DECIMAL(8,2)  DEFAULT 0 COMMENT '距离 km',
  `intensity`     VARCHAR(20)   DEFAULT 'moderate' COMMENT '强度：low/moderate/high',
  `notes`         TEXT          COMMENT '备注',
  `created_at`    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_exercise_user_date` (`user_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运动记录';

-- ============================================================================
-- 11. 家属绑定表
-- ============================================================================
DROP TABLE IF EXISTS `family_bindings`;
CREATE TABLE `family_bindings` (
  `id`          VARCHAR(36)  NOT NULL COMMENT '绑定ID',
  `elder_id`    VARCHAR(36)  NOT NULL COMMENT '长辈用户ID',
  `family_id`   VARCHAR(36)  NOT NULL COMMENT '家属用户ID',
  `invite_code` VARCHAR(20)  NOT NULL COMMENT '邀请码',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending/accepted/rejected',
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)  DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_binding_elder` (`elder_id`),
  KEY `idx_binding_family` (`family_id`),
  KEY `idx_binding_invite` (`invite_code`),
  KEY `idx_binding_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长辈-家属绑定关系';

-- ============================================================================
-- 12. 服药记录表
-- ============================================================================
DROP TABLE IF EXISTS `medicine_records`;
CREATE TABLE `medicine_records` (
  `id`            VARCHAR(36)   NOT NULL COMMENT '记录ID',
  `reminder_id`   VARCHAR(36)   DEFAULT NULL COMMENT '关联的提醒',
  `user_id`       VARCHAR(36)   NOT NULL COMMENT '用户ID',
  `medicine_name` VARCHAR(128)  NOT NULL COMMENT '药品名称',
  `taken_time`    DATETIME(3)   NOT NULL COMMENT '服药时间',
  `taken_date`    DATE          NOT NULL COMMENT '服药日期',
  `status`        VARCHAR(20)   NOT NULL DEFAULT 'taken' COMMENT '状态：taken/missed/skipped',
  `notes`         TEXT          COMMENT '备注',
  `created_at`    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_med_rec_user_date` (`user_id`, `taken_date`),
  KEY `idx_med_rec_reminder` (`reminder_id`),
  KEY `idx_med_rec_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服药记录';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 初始化数据
-- ============================================================================

-- 健康检查占位
INSERT INTO `health_check` (`id`) VALUES (1);

-- 演示用户（长辈）
INSERT INTO `users` (`id`, `phone`, `name`, `role`, `gender`, `is_active`) VALUES
('demo-elder-0001', '13800000001', '张爷爷', 'elder', 'male', 1),
('demo-family-0001', '13800000002', '张小明', 'family', 'male', 1);

-- 演示健康档案
INSERT INTO `elder_info` (`id`, `user_id`, `blood_type`, `height`, `weight`,
                          `chronic_diseases`, `allergies`, `diet_preference`,
                          `wake_time_target`, `sleep_time_target`, `exercise_frequency`)
VALUES ('demo-elder-info-0001', 'demo-elder-0001', 'A', 170.00, 65.00,
        JSON_ARRAY('高血压'), JSON_ARRAY('海鲜'), JSON_ARRAY('清淡'),
        '06:30', '22:00', 'daily');

-- 演示绑定关系
INSERT INTO `family_bindings` (`id`, `elder_id`, `family_id`, `invite_code`, `status`)
VALUES ('demo-binding-0001', 'demo-elder-0001', 'demo-family-0001', 'FAM12345', 'accepted');

-- 演示用药提醒
INSERT INTO `medicine_reminders` (`id`, `user_id`, `medicine_name`, `dosage`,
                                  `frequency`, `times`, `start_date`, `is_active`)
VALUES ('demo-reminder-0001', 'demo-elder-0001', '降压药', '1片',
        'twice', JSON_ARRAY('08:00', '20:00'), CURDATE(), 1);

-- ============================================================================
-- 验证
-- ============================================================================
SELECT
  TABLE_NAME    AS `表名`,
  TABLE_COMMENT AS `说明`,
  TABLE_ROWS    AS `行数`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'znwg'
ORDER BY TABLE_NAME;
