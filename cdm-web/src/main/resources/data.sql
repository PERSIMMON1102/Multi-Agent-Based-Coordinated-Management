-- 初始化数据库脚本
-- 使用 INSERT IGNORE 防止重复插入

-- 医疗资源初始数据
INSERT IGNORE INTO medical_resource (id, name, type, address, latitude, longitude, specialty, phone, level) VALUES
(1, '社区卫生服务中心', '社区医院', '北京市朝阳区XX街道1号', 39.9042, 116.4074, '全科,内科,心内科', '010-12345678', '一级'),
(2, '区人民医院', '综合医院', '北京市朝阳区YY路10号', 39.9142, 116.4174, '心内科,内分泌科,呼吸内科,肾内科,神经内科', '010-23456789', '二级'),
(3, '市第一医院', '三甲医院', '北京市东城区ZZ大街100号', 39.9242, 116.4274, '心内科,内分泌科,呼吸内科,肾内科,神经内科,康复科', '010-34567890', '三级'),
(4, '康复医疗中心', '专科医院', '北京市海淀区AA路20号', 39.9342, 116.4374, '康复科,神经康复,心脏康复', '010-45678901', '二级'),
(5, '糖尿病专科医院', '专科医院', '北京市西城区BB街5号', 39.9442, 116.4474, '内分泌科,糖尿病足,眼科', '010-56789012', '二级');

-- 账号初始化策略
-- 1. 首个管理员账号通过启动配置注入，不在 SQL 中硬编码默认凭据
-- 2. 医生账号由管理员后台创建
-- 3. 患者账号通过前台注册入口自行创建

-- 知识库表（仅第一次初始化时生效）
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    disease VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    section VARCHAR(200),
    content TEXT NOT NULL,
    source VARCHAR(200),
    published_at DATE,
    version_tag VARCHAR(50),
    embedding TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_knowledge_disease (disease),
    INDEX idx_knowledge_version (version_tag)
);

-- AI健康顾问会话与消息（若不存在则创建）
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id VARCHAR(64) PRIMARY KEY,
    patient_id BIGINT,
    owner_role VARCHAR(20),
    owner_user_id BIGINT,
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP,
    INDEX idx_ai_session_patient (patient_id),
    INDEX idx_ai_session_owner (owner_role, owner_user_id),
    INDEX idx_ai_session_last (last_message_at)
);

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    references_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_msg_session (session_id),
    INDEX idx_ai_msg_created (created_at)
);
