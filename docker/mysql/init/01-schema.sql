-- =====================================================================
-- 洪兴洗车电商服务平台 - 初始化脚本 01：建库建表
-- MySQL 8.0 / InnoDB / utf8mb4
--
-- 按微服务分库（database-per-service）：
--   hc_user    -> user-service    用户、关注、点赞
--   hc_shop    -> shop-service    门店、养车服务项目
--   hc_product -> product-service 商品分类、商品（库存）
--   hc_order   -> order-service   订单、订单明细、订单状态流转日志
--   hc_pay     -> pay-service     支付流水（out_trade_no 唯一索引，回调幂等）
--   hc_review  -> review-service  服务评价
--
-- 主键策略：BIGINT，运行时由 MyBatis-Plus 雪花算法（ASSIGN_ID）生成；
--           种子数据使用固定小 ID，与雪花 ID 不会冲突。
-- 执行方式：Docker 首次启动自动执行；或手动
--   mysql -h127.0.0.1 -P3307 -uroot -p123456 < 01-schema.sql
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 用户库 hc_user
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hc_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE hc_user;

DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT       NOT NULL                          COMMENT '用户ID（雪花）',
    username    VARCHAR(50)  NOT NULL                          COMMENT '登录名',
    password    VARCHAR(100) NOT NULL                          COMMENT '密码（BCrypt 哈希）',
    nickname    VARCHAR(50)  DEFAULT NULL                      COMMENT '昵称',
    phone       VARCHAR(20)  DEFAULT NULL                      COMMENT '手机号',
    avatar      VARCHAR(255) DEFAULT NULL                      COMMENT '头像URL',
    gender      TINYINT      NOT NULL DEFAULT 0                COMMENT '性别：0未知 1男 2女',
    status      TINYINT      NOT NULL DEFAULT 1                COMMENT '状态：1正常 0禁用',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER'           COMMENT '角色：USER普通用户 ADMIN管理员',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表';

DROP TABLE IF EXISTS t_user_follow;
CREATE TABLE t_user_follow (
    id          BIGINT   NOT NULL                          COMMENT '主键（雪花）',
    user_id     BIGINT   NOT NULL                          COMMENT '发起关注的用户ID',
    target_type TINYINT  NOT NULL                          COMMENT '关注目标类型：1门店 2用户',
    target_id   BIGINT   NOT NULL                          COMMENT '关注目标ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    KEY idx_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户关注表';

DROP TABLE IF EXISTS t_user_like;
CREATE TABLE t_user_like (
    id          BIGINT   NOT NULL                          COMMENT '主键（雪花）',
    user_id     BIGINT   NOT NULL                          COMMENT '点赞用户ID',
    target_type TINYINT  NOT NULL                          COMMENT '点赞目标类型：1评价 2服务项目 3商品',
    target_id   BIGINT   NOT NULL                          COMMENT '点赞目标ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    KEY idx_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户点赞表';

-- ---------------------------------------------------------------------
-- 2. 门店库 hc_shop
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hc_shop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE hc_shop;

DROP TABLE IF EXISTS t_shop;
CREATE TABLE t_shop (
    id             BIGINT        NOT NULL                          COMMENT '门店ID（雪花）',
    shop_name      VARCHAR(100)  NOT NULL                          COMMENT '门店名称',
    logo           VARCHAR(255)  DEFAULT NULL                      COMMENT '门店Logo URL',
    contact_phone  VARCHAR(20)   DEFAULT NULL                      COMMENT '联系电话',
    province       VARCHAR(30)   DEFAULT NULL                      COMMENT '省',
    city           VARCHAR(30)   DEFAULT NULL                      COMMENT '市',
    district       VARCHAR(30)   DEFAULT NULL                      COMMENT '区',
    address        VARCHAR(255)  DEFAULT NULL                      COMMENT '详细地址',
    longitude      DECIMAL(10,7) DEFAULT NULL                      COMMENT '经度',
    latitude       DECIMAL(10,7) DEFAULT NULL                      COMMENT '纬度',
    business_hours VARCHAR(50)   DEFAULT NULL                      COMMENT '营业时间，如 08:00-20:00',
    description    VARCHAR(500)  DEFAULT NULL                      COMMENT '门店简介',
    score          DECIMAL(2,1)  NOT NULL DEFAULT 5.0              COMMENT '综合评分（1.0~5.0）',
    sales          INT           NOT NULL DEFAULT 0                COMMENT '累计服务单数',
    status         TINYINT       NOT NULL DEFAULT 1                COMMENT '状态：1营业 0停业',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (id),
    KEY idx_city (city),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '养车门店表';

DROP TABLE IF EXISTS t_service_item;
CREATE TABLE t_service_item (
    id             BIGINT        NOT NULL                          COMMENT '服务项目ID（雪花）',
    shop_id        BIGINT        NOT NULL                          COMMENT '所属门店ID',
    item_name      VARCHAR(100)  NOT NULL                          COMMENT '服务项目名称',
    category       VARCHAR(30)   NOT NULL                          COMMENT '服务分类：保养/洗车/轮胎/美容/检测/救援',
    cover_img      VARCHAR(255)  DEFAULT NULL                      COMMENT '封面图URL',
    description    VARCHAR(1000) DEFAULT NULL                      COMMENT '服务详情',
    price          DECIMAL(10,2) NOT NULL                          COMMENT '售价（元）',
    original_price DECIMAL(10,2) DEFAULT NULL                      COMMENT '划线原价（元）',
    duration_min   INT           NOT NULL DEFAULT 30               COMMENT '预计服务时长（分钟）',
    sales          INT           NOT NULL DEFAULT 0                COMMENT '销量',
    status         TINYINT       NOT NULL DEFAULT 1                COMMENT '状态：1上架 0下架',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (id),
    KEY idx_shop (shop_id),
    KEY idx_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '养车服务项目表';

-- ---------------------------------------------------------------------
-- 3. 商品库 hc_product
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hc_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE hc_product;

DROP TABLE IF EXISTS t_category;
CREATE TABLE t_category (
    id            BIGINT      NOT NULL                          COMMENT '分类ID',
    category_name VARCHAR(50) NOT NULL                          COMMENT '分类名称',
    parent_id     BIGINT      NOT NULL DEFAULT 0                COMMENT '父分类ID，0为顶级',
    sort_order    INT         NOT NULL DEFAULT 0                COMMENT '排序值（越小越靠前）',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品分类表';

DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
    id           BIGINT        NOT NULL                          COMMENT '商品ID（雪花）',
    category_id  BIGINT        NOT NULL                          COMMENT '分类ID',
    product_name VARCHAR(150)  NOT NULL                          COMMENT '商品名称',
    brand        VARCHAR(50)   DEFAULT NULL                      COMMENT '品牌',
    cover_img    VARCHAR(255)  DEFAULT NULL                      COMMENT '封面图URL',
    images       VARCHAR(1000) DEFAULT NULL                      COMMENT '轮播图URL，JSON数组',
    description  TEXT                                            COMMENT '商品详情',
    price        DECIMAL(10,2) NOT NULL                          COMMENT '售价（元）',
    stock        INT           NOT NULL DEFAULT 0                COMMENT '可售库存（扣减用 UPDATE ... WHERE stock >= n 防超卖）',
    sales        INT           NOT NULL DEFAULT 0                COMMENT '销量',
    status       TINYINT       NOT NULL DEFAULT 1                COMMENT '状态：1上架 0下架',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品表';

DROP TABLE IF EXISTS t_cart_item;
CREATE TABLE t_cart_item (
    id           BIGINT        NOT NULL                          COMMENT '主键（雪花）',
    user_id      BIGINT        NOT NULL                          COMMENT '用户ID',
    product_id   BIGINT        NOT NULL                          COMMENT '商品ID',
    quantity     INT           NOT NULL DEFAULT 1                COMMENT '加购数量',
    selected     TINYINT       NOT NULL DEFAULT 1                COMMENT '是否勾选：1是 0否',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车表';

-- ---------------------------------------------------------------------
-- 4. 订单库 hc_order
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hc_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE hc_order;

DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id            BIGINT        NOT NULL                          COMMENT '订单ID（雪花）',
    order_no      VARCHAR(32)   NOT NULL                          COMMENT '订单号（业务唯一）',
    user_id       BIGINT        NOT NULL                          COMMENT '下单用户ID',
    shop_id       BIGINT        DEFAULT NULL                      COMMENT '门店ID（服务订单必填，商品订单可空）',
    order_type    TINYINT       NOT NULL                          COMMENT '订单类型：1商品 2养车服务',
    total_amount  DECIMAL(10,2) NOT NULL                          COMMENT '订单总额（元）',
    pay_amount    DECIMAL(10,2) NOT NULL                          COMMENT '应付金额（元）',
    status        TINYINT       NOT NULL DEFAULT 0                COMMENT '状态机：0待支付 10已支付 20服务中/已发货 30已完成 40已取消 50退款中 60已退款',
    pay_time      DATETIME      DEFAULT NULL                      COMMENT '支付时间',
    finish_time   DATETIME      DEFAULT NULL                      COMMENT '完成时间',
    cancel_time   DATETIME      DEFAULT NULL                      COMMENT '取消时间',
    cancel_reason VARCHAR(255)  DEFAULT NULL                      COMMENT '取消原因（超时取消/用户取消）',
    appointment_time    DATETIME     DEFAULT NULL                 COMMENT '服务预约时间（服务订单使用）',
    appointment_contact VARCHAR(50)  DEFAULT NULL                 COMMENT '服务预约联系人',
    appointment_phone   VARCHAR(20)  DEFAULT NULL                 COMMENT '服务预约联系电话',
    remark        VARCHAR(255)  DEFAULT NULL                      COMMENT '订单备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_status (user_id, status),
    KEY idx_shop (shop_id),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单表';

DROP TABLE IF EXISTS t_order_item;
CREATE TABLE t_order_item (
    id           BIGINT        NOT NULL                          COMMENT '主键（雪花）',
    order_id     BIGINT        NOT NULL                          COMMENT '订单ID',
    order_no     VARCHAR(32)   NOT NULL                          COMMENT '订单号（冗余，便于查询）',
    item_type    TINYINT       NOT NULL                          COMMENT '明细类型：1商品 2服务项目',
    item_id      BIGINT        NOT NULL                          COMMENT '商品ID/服务项目ID',
    item_name    VARCHAR(150)  NOT NULL                          COMMENT '名称快照',
    item_img     VARCHAR(255)  DEFAULT NULL                      COMMENT '图片快照',
    price        DECIMAL(10,2) NOT NULL                          COMMENT '下单单价（元）',
    quantity     INT           NOT NULL DEFAULT 1                COMMENT '数量',
    total_amount DECIMAL(10,2) NOT NULL                          COMMENT '小计金额（元）',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单明细表';

DROP TABLE IF EXISTS t_order_status_log;
CREATE TABLE t_order_status_log (
    id            BIGINT      NOT NULL                          COMMENT '主键（雪花）',
    order_id      BIGINT      NOT NULL                          COMMENT '订单ID',
    order_no      VARCHAR(32) NOT NULL                          COMMENT '订单号',
    from_status   TINYINT     DEFAULT NULL                      COMMENT '原状态（创建时为空）',
    to_status     TINYINT     NOT NULL                          COMMENT '新状态',
    event         VARCHAR(50) NOT NULL                          COMMENT '触发事件：CREATE/PAY/SERVICE/DELIVER/FINISH/CANCEL_TIMEOUT/CANCEL_USER/REFUND',
    operator_type TINYINT     NOT NULL DEFAULT 1                COMMENT '操作方：1用户 2系统 3商家',
    remark        VARCHAR(255) DEFAULT NULL                     COMMENT '备注',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流转时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单状态流转日志表';

-- ---------------------------------------------------------------------
-- 5. 支付库 hc_pay
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hc_pay DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE hc_pay;

DROP TABLE IF EXISTS t_pay_transaction;
CREATE TABLE t_pay_transaction (
    id             BIGINT        NOT NULL                          COMMENT '主键（雪花）',
    out_trade_no   VARCHAR(64)   NOT NULL                          COMMENT '商户支付流水号（全局唯一，支付回调幂等键）',
    order_no       VARCHAR(32)   NOT NULL                          COMMENT '业务订单号（一单可多次发起支付，普通索引）',
    user_id        BIGINT        NOT NULL                          COMMENT '支付用户ID',
    amount         DECIMAL(10,2) NOT NULL                          COMMENT '支付金额（元）',
    channel        VARCHAR(20)   NOT NULL DEFAULT 'MOCK'           COMMENT '支付渠道：MOCK模拟/ALIPAY/WECHAT',
    status         TINYINT       NOT NULL DEFAULT 0                COMMENT '支付状态：0支付中 1成功 2失败 3已关闭',
    third_trade_no VARCHAR(64)   DEFAULT NULL                      COMMENT '第三方平台交易号',
    pay_time       DATETIME      DEFAULT NULL                      COMMENT '支付完成时间',
    callback_time  DATETIME      DEFAULT NULL                      COMMENT '最近一次回调时间',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_order_no (order_no),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '支付流水表';

-- ---------------------------------------------------------------------
-- 6. 评价库 hc_review
-- ---------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS hc_review DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE hc_review;

DROP TABLE IF EXISTS t_review;
CREATE TABLE t_review (
    id          BIGINT        NOT NULL                          COMMENT '评价ID（雪花）',
    order_id    BIGINT        NOT NULL                          COMMENT '订单ID',
    order_no    VARCHAR(32)   NOT NULL                          COMMENT '订单号',
    user_id     BIGINT        NOT NULL                          COMMENT '评价用户ID',
    shop_id     BIGINT        DEFAULT NULL                      COMMENT '门店ID（服务评价必填）',
    item_type   TINYINT       NOT NULL                          COMMENT '评价对象类型：1商品 2服务项目',
    item_id     BIGINT        NOT NULL                          COMMENT '商品ID/服务项目ID',
    score       TINYINT       NOT NULL                          COMMENT '评分：1~5星',
    content     VARCHAR(1000) DEFAULT NULL                      COMMENT '评价内容',
    images      VARCHAR(1000) DEFAULT NULL                      COMMENT '晒图URL，JSON数组',
    reply       VARCHAR(500)  DEFAULT NULL                      COMMENT '商家回复',
    reply_time  DATETIME      DEFAULT NULL                      COMMENT '商家回复时间',
    like_count  INT           NOT NULL DEFAULT 0                COMMENT '点赞数（user-service 点赞事件异步同步）',
    status      TINYINT       NOT NULL DEFAULT 1                COMMENT '状态：1显示 0隐藏',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item (order_id, item_id),
    KEY idx_shop (shop_id),
    KEY idx_user (user_id),
    KEY idx_item (item_type, item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '服务评价表';