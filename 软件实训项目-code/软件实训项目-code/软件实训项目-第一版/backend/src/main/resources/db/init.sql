-- ============================================================
-- AI智能求职辅导平台 - 数据库初始化脚本
-- 包含：建库 + 15张表 DDL + 预置数据
-- MySQL 8.0+
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS career_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE career_platform;

-- ============================================================
-- 1. 用户账号表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名（唯一）',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    `role` VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT/HR/ADMIN',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE/DISABLED',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0正常/1删除)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

-- ============================================================
-- 2. 求职画像表
-- ============================================================
CREATE TABLE IF NOT EXISTS `career_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '画像ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `school` VARCHAR(100) DEFAULT NULL COMMENT '学校',
    `major` VARCHAR(100) DEFAULT NULL COMMENT '专业',
    `education` VARCHAR(20) DEFAULT NULL COMMENT '学历：专科/本科/硕士/博士',
    `grade` VARCHAR(20) DEFAULT NULL COMMENT '年级',
    `skill_tags` JSON DEFAULT NULL COMMENT '技能标签(JSON数组)',
    `target_roles` JSON DEFAULT NULL COMMENT '目标岗位(JSON数组)',
    `expected_city` VARCHAR(50) DEFAULT NULL COMMENT '期望城市',
    `expected_salary` VARCHAR(50) DEFAULT NULL COMMENT '期望薪资',
    `job_status` VARCHAR(20) DEFAULT NULL COMMENT '求职状态',
    `summary` TEXT DEFAULT NULL COMMENT '个人总结',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='求职画像表';

-- ============================================================
-- 3. 技能词典表
-- ============================================================
CREATE TABLE IF NOT EXISTS `skill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '技能ID',
    `name` VARCHAR(50) NOT NULL COMMENT '技能名称',
    `category` VARCHAR(50) NOT NULL COMMENT '类别：编程语言/框架/数据库/工具/软技能',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '技能描述',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0正常/1删除)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能词典表';

-- ============================================================
-- 4. 岗位表
-- ============================================================
CREATE TABLE IF NOT EXISTS `job_position` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    `title` VARCHAR(100) NOT NULL COMMENT '岗位名称',
    `direction` VARCHAR(50) DEFAULT NULL COMMENT '岗位方向',
    `jd` TEXT DEFAULT NULL COMMENT '岗位描述',
    `city` VARCHAR(50) DEFAULT NULL COMMENT '工作城市',
    `salary_range` VARCHAR(50) DEFAULT NULL COMMENT '薪资范围',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0正常/1删除)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_direction` (`direction`),
    KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位表';

-- ============================================================
-- 5. 岗位技能要求表
-- ============================================================
CREATE TABLE IF NOT EXISTS `job_skill_requirement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `job_id` BIGINT NOT NULL COMMENT '岗位ID',
    `skill_id` BIGINT NOT NULL COMMENT '技能ID',
    `required_level` VARCHAR(20) NOT NULL DEFAULT '掌握' COMMENT '要求等级：了解/掌握/熟练/精通',
    `weight` DOUBLE NOT NULL DEFAULT 1.0 COMMENT '权重',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_skill` (`job_id`, `skill_id`),
    KEY `idx_job_id` (`job_id`),
    KEY `idx_skill_id` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位技能要求表';

-- ============================================================
-- 6. 测评题库表
-- ============================================================
CREATE TABLE IF NOT EXISTS `assessment_question` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `dimension` VARCHAR(30) NOT NULL COMMENT '维度：PROGRAMMING/LOGIC/PRODUCT/TECH/COMMUNICATION',
    `type` VARCHAR(20) NOT NULL DEFAULT 'SELECT' COMMENT '题型：SELECT/JUDGE',
    `content` TEXT NOT NULL COMMENT '题目内容',
    `options` JSON DEFAULT NULL COMMENT '选项(JSON数组)',
    `answer` VARCHAR(10) NOT NULL COMMENT '正确答案',
    `score` INT NOT NULL DEFAULT 5 COMMENT '分值',
    `difficulty` VARCHAR(10) NOT NULL DEFAULT '中等' COMMENT '难度：简单/中等/困难',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0正常/1删除)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_dimension` (`dimension`),
    KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测评题库表';

-- ============================================================
-- 7. 测评结果表
-- ============================================================
CREATE TABLE IF NOT EXISTS `assessment_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '结果ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(30) NOT NULL DEFAULT 'COMPREHENSIVE' COMMENT '测评类型',
    `programming_score` DOUBLE DEFAULT 0 COMMENT '编程能力得分',
    `logic_score` DOUBLE DEFAULT 0 COMMENT '逻辑推理得分',
    `product_score` DOUBLE DEFAULT 0 COMMENT '产品思维得分',
    `tech_score` DOUBLE DEFAULT 0 COMMENT '技术素养得分',
    `communication_score` DOUBLE DEFAULT 0 COMMENT '沟通表达得分',
    `total_score` DOUBLE DEFAULT 0 COMMENT '综合总分',
    `result_json` JSON DEFAULT NULL COMMENT '详细结果(JSON)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测评结果表';

-- ============================================================
-- 8. 推荐记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `recommendation_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(30) NOT NULL COMMENT '类型：CAREER_EXPLORATION/JOB_MATCH/GAP_ANALYSIS/ENTERPRISE_REC/GROWTH_REPORT',
    `input_text` TEXT DEFAULT NULL COMMENT '输入文本',
    `result_json` JSON DEFAULT NULL COMMENT '结果详情(JSON)',
    `source` VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '来源：AI/FALLBACK/RULE/CACHE',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_type` (`user_id`, `type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐记录表';

-- ============================================================
-- 9. 学习路径表
-- ============================================================
CREATE TABLE IF NOT EXISTS `learning_path` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '路径ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `target_job_id` BIGINT DEFAULT NULL COMMENT '目标岗位ID',
    `daily_hours` DOUBLE NOT NULL DEFAULT 2.0 COMMENT '每日学习小时',
    `total_days` INT NOT NULL DEFAULT 30 COMMENT '预计总天数',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/COMPLETED/ARCHIVED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径表';

-- ============================================================
-- 10. 学习任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS `learning_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `path_id` BIGINT NOT NULL COMMENT '路径ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `skill_id` BIGINT DEFAULT NULL COMMENT '技能ID',
    `title` VARCHAR(200) NOT NULL COMMENT '任务标题',
    `description` TEXT DEFAULT NULL COMMENT '任务描述',
    `resource_url` VARCHAR(500) DEFAULT NULL COMMENT '学习资源URL',
    `stage` VARCHAR(20) NOT NULL COMMENT '阶段：BASIC/FRAMEWORK/PROJECT/INTERVIEW',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/IN_PROGRESS/COMPLETED',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序编号',
    `due_date` DATE DEFAULT NULL COMMENT '截止日期',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_path_id` (`path_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_stage` (`stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习任务表';

-- ============================================================
-- 11. 学习资源库表
-- ============================================================
CREATE TABLE IF NOT EXISTS `learning_resource` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `skill_id` BIGINT NOT NULL COMMENT '技能ID',
    `stage` VARCHAR(20) NOT NULL COMMENT '阶段：BASIC/FRAMEWORK/PROJECT/INTERVIEW',
    `type` VARCHAR(20) NOT NULL COMMENT '类型：ARTICLE/VIDEO/EXERCISE/PROJECT',
    `title` VARCHAR(200) NOT NULL COMMENT '资源标题',
    `url` VARCHAR(500) DEFAULT NULL COMMENT '资源链接',
    `description` TEXT DEFAULT NULL COMMENT '资源描述',
    `difficulty` VARCHAR(10) NOT NULL DEFAULT '中等' COMMENT '难度',
    `estimated_hours` DOUBLE DEFAULT 1.0 COMMENT '预计学习小时',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0正常/1删除)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_skill_id` (`skill_id`),
    KEY `idx_stage` (`stage`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习资源库表';

-- ============================================================
-- 12. 简历分析表
-- ============================================================
CREATE TABLE IF NOT EXISTS `resume_analysis` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分析ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `target_job_id` BIGINT DEFAULT NULL COMMENT '目标岗位ID',
    `file_url` VARCHAR(500) DEFAULT NULL COMMENT '简历文件路径',
    `score` DOUBLE DEFAULT 0 COMMENT 'AI综合评分',
    `result_json` JSON DEFAULT NULL COMMENT '分析结果(JSON)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历分析表';

-- ============================================================
-- 13. 模拟面试记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `interview_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `target_job_id` BIGINT DEFAULT NULL COMMENT '目标岗位ID',
    `interview_type` VARCHAR(20) NOT NULL COMMENT '类型：TECHNICAL/HR/COMPREHENSIVE',
    `question_json` JSON DEFAULT NULL COMMENT '问答详情(JSON)',
    `report_json` JSON DEFAULT NULL COMMENT '评估报告(JSON)',
    `score` DOUBLE DEFAULT 0 COMMENT '综合评分',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟面试记录表';

-- ============================================================
-- 14. 常见问题表（FAQ）
-- ============================================================
CREATE TABLE IF NOT EXISTS `faq` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'FAQ ID',
    `category` VARCHAR(30) NOT NULL COMMENT '分类：PLATFORM/POSITION/LEARNING',
    `question` VARCHAR(500) NOT NULL COMMENT '问题',
    `answer` TEXT NOT NULL COMMENT '答案',
    `keywords` VARCHAR(500) DEFAULT NULL COMMENT '关键词',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序编号',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除(0正常/1删除)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='常见问题表';

-- ============================================================
-- 15. AI调用日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_call_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `scene` VARCHAR(50) NOT NULL COMMENT '场景',
    `prompt_summary` VARCHAR(500) DEFAULT NULL COMMENT 'Prompt摘要',
    `request_hash` VARCHAR(64) DEFAULT NULL COMMENT '请求哈希（缓存去重）',
    `response_source` VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '来源：AI/CACHE/FALLBACK/RULE',
    `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/FALLBACK',
    `duration_ms` BIGINT DEFAULT 0 COMMENT '耗时(毫秒)',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
    `fallback_reason` VARCHAR(500) DEFAULT NULL COMMENT '降级原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_scene` (`scene`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用日志表';

-- ============================================================
-- 预置数据
-- ============================================================

-- 管理员账号（密码 admin123 的 BCrypt 哈希）
INSERT INTO `user` (`username`, `password_hash`, `role`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'ADMIN', 'ACTIVE');

-- 技能词典 (50+)
INSERT INTO `skill` (`name`, `category`, `description`) VALUES
('Java', '编程语言', 'Java 是一种广泛使用的面向对象编程语言，用于企业级应用开发'),
('Python', '编程语言', 'Python 是一种解释型、面向对象的高级编程语言，广泛用于AI和数据科学'),
('JavaScript', '编程语言', 'JavaScript 是 Web 开发的核心语言，支持前端和后端开发'),
('TypeScript', '编程语言', 'TypeScript 是 JavaScript 的超集，增加了静态类型检查'),
('Go', '编程语言', 'Go 是 Google 开发的高性能系统语言，适合微服务和云原生开发'),
('C++', '编程语言', 'C++ 是一种高性能编程语言，广泛用于系统开发、游戏引擎和算法竞赛'),
('Spring Boot', '框架', 'Spring Boot 是 Java 生态最流行的微服务开发框架'),
('Spring Cloud', '框架', 'Spring Cloud 是一套微服务治理解决方案'),
('MyBatis', '框架', 'MyBatis 是 Java 持久层框架，简化数据库操作'),
('Vue.js', '框架', 'Vue.js 是一款渐进式前端框架，适合构建SPA应用'),
('React', '框架', 'React 是 Facebook 推出的前端UI框架，以组件化为核心'),
('Django', '框架', 'Django 是 Python Web 开发的全栈框架'),
('Flask', '框架', 'Flask 是 Python 轻量级 Web 框架'),
('Node.js', '框架', 'Node.js 是基于 Chrome V8 引擎的 JavaScript 运行时'),
('MySQL', '数据库', 'MySQL 是最流行的开源关系型数据库'),
('Redis', '数据库', 'Redis 是开源的内存数据结构存储，用作数据库、缓存和消息代理'),
('MongoDB', '数据库', 'MongoDB 是一种 NoSQL 文档型数据库'),
('PostgreSQL', '数据库', 'PostgreSQL 是功能强大的开源关系型数据库'),
('Elasticsearch', '数据库', 'Elasticsearch 是分布式搜索和数据分析引擎'),
('Git', '工具', 'Git 是分布式版本控制系统，团队协作必备技能'),
('Docker', '工具', 'Docker 是容器化平台，用于应用打包和部署'),
('Kubernetes', '工具', 'Kubernetes 是容器编排平台，管理容器化应用'),
('Linux', '工具', 'Linux 操作系统命令与Shell脚本编写能力'),
('Nginx', '工具', 'Nginx 是高性能 Web 服务器和反向代理'),
('Maven', '工具', 'Maven 是 Java 项目构建与依赖管理工具'),
('Jenkins', '工具', 'Jenkins 是开源的持续集成/持续部署(CI/CD)工具'),
('RESTful API', '软技能', 'RESTful API 设计方法与最佳实践'),
('数据结构', '软技能', '掌握常用数据结构：数组、链表、树、图、哈希表等'),
('算法', '软技能', '掌握常用算法：排序、搜索、动态规划、贪心等'),
('面向对象设计', '软技能', '面向对象设计原则与设计模式'),
('系统设计', '软技能', '掌握分布式系统的架构设计方法'),
('数据库设计', '软技能', '掌握数据库表结构设计与SQL优化'),
('微服务架构', '软技能', '微服务设计原则、服务拆分与治理'),
('敏捷开发', '软技能', 'Scrum、Kanban等敏捷开发方法论'),
('沟通协作', '软技能', '团队沟通、文档编写、需求理解能力'),
('HTML/CSS', '编程语言', 'Web 页面结构与样式设计基础'),
('R', '编程语言', 'R 语言是统计分析专用编程语言，用于数据分析'),
('Scala', '编程语言', 'Scala 是融合面向对象与函数式编程的JVM语言'),
('Rust', '编程语言', 'Rust 是注重内存安全的系统级编程语言'),
('Hibernate', '框架', 'Hibernate 是 Java ORM 框架，自动映射对象到关系型数据库'),
('Netty', '框架', 'Netty 是高性能异步网络通信框架'),
('RabbitMQ', '工具', 'RabbitMQ 是开源消息队列中间件'),
('Kafka', '工具', 'Kafka 是分布式流处理平台，用于高吞吐量消息队列'),
('Tomcat', '工具', 'Tomcat 是 Java Web 应用程序服务器'),
('Spring Security', '框架', 'Spring Security 是身份认证与访问控制框架'),
('MyBatis-Plus', '框架', 'MyBatis-Plus 是 MyBatis 的增强工具包'),
('GraphQL', '框架', 'GraphQL 是一种用于API的查询语言'),
('JUnit', '工具', 'JUnit 是 Java 单元测试框架'),
('Mockito', '工具', 'Mockito 是 Java Mock 测试框架'),
('Swagger', '工具', 'Swagger 是 RESTful API 文档自动生成工具'),
('Gradle', '工具', 'Gradle 是基于 Groovy 的项目构建工具');

-- 岗位数据 (20+)
INSERT INTO `job_position` (`title`, `direction`, `jd`, `city`, `salary_range`) VALUES
('Java后端开发工程师', '后端开发', '负责公司核心业务系统的后端设计与开发，参与微服务架构设计，保证系统高可用和高性能。', '深圳', '15K-30K'),
('前端开发工程师', '前端开发', '负责产品前端页面开发与性能优化，与UI设计师和后端协作完成产品迭代。', '北京', '12K-25K'),
('Python数据工程师', '数据', '负责数据平台建设、数据清洗和数据分析，为业务决策提供数据支持。', '上海', '18K-35K'),
('产品经理', '产品', '负责产品规划、需求分析和功能设计，协调研发、设计、运营推进产品落地。', '杭州', '15K-30K'),
('测试开发工程师', '测试', '设计测试用例，搭建自动化测试框架，保证产品质量。', '深圳', '12K-22K'),
('全栈开发工程师', '后端开发', '负责Web产品前后端全链路开发，从数据库设计到前端页面实现。', '广州', '15K-28K'),
('算法工程师', '数据', '负责机器学习和推荐算法研发，优化模型效果并部署到生产环境。', '北京', '25K-50K'),
('DevOps运维工程师', '运维', '负责CI/CD流水线搭建、容器化部署和线上运维监控。', '深圳', '15K-28K'),
('Android开发工程师', '移动开发', '负责Android客户端的架构设计与功能开发，优化应用性能。', '上海', '15K-30K'),
('iOS开发工程师', '移动开发', '负责iOS客户端的开发与维护，保障应用质量和用户体验。', '北京', '15K-30K'),
('Golang后端开发工程师', '后端开发', '使用Go语言开发商性能后端服务，参与云原生微服务架构建设。', '深圳', '18K-35K'),
('安全工程师', '安全', '负责公司信息安全体系建设，包括渗透测试、安全审计和漏洞修复。', '北京', '20K-40K'),
('UI/UX设计师', '设计', '负责产品界面视觉设计、交互设计和用户体验优化。', '杭州', '12K-25K'),
('技术项目经理', '管理', '负责技术团队的日常管理、进度跟踪和跨团队沟通协调。', '深圳', '20K-35K'),
('数据库管理员(DBA)', '数据', '负责MySQL/Redis数据库运维、性能调优和容灾备份。', '上海', '18K-30K'),
('嵌入式开发工程师', '嵌入式', '负责嵌入式系统研发，包括底层驱动和应用程序开发。', '广州', '15K-28K'),
('数据分析师', '数据', '负责业务数据分析和商业智能(BI)报表建设，推动数据驱动决策。', '成都', '10K-20K'),
('C++系统开发工程师', '后端开发', '负责高性能计算和系统底层模块的开发与优化。', '深圳', '20K-40K'),
('区块链开发工程师', '后端开发', '负责区块链核心协议及智能合约的设计和开发。', '北京', '25K-45K'),
('AI应用开发工程师', '数据', '将AI模型集成到业务应用中，负责模型部署和推理优化。', '上海', '20K-40K'),
('软件测试工程师', '测试', '执行功能测试、性能测试和兼容性测试，编写测试报告。', '武汉', '8K-15K'),
('运维工程师(Linux)', '运维', '负责Linux服务器日常运维、监控和故障排查。', '深圳', '10K-20K');

-- 岗位技能要求关联数据（每个岗位关联3-8个技能）
INSERT INTO `job_skill_requirement` (`job_id`, `skill_id`, `required_level`, `weight`) VALUES
-- Java后端开发 (1)
(1,1,'精通',3.0),(1,7,'精通',3.0),(1,9,'熟练',2.0),(1,15,'熟练',2.0),(1,16,'掌握',1.5),(1,25,'掌握',1.0),(1,27,'熟练',2.0),(1,28,'熟练',2.0),
-- 前端开发 (2)
(2,3,'精通',3.0),(2,4,'熟练',2.0),(2,10,'精通',2.5),(2,11,'掌握',1.5),(2,36,'精通',2.0),(2,6,'了解',0.5),
-- Python数据工程师 (3)
(3,2,'精通',3.0),(3,15,'熟练',2.0),(3,17,'掌握',1.5),(3,28,'熟练',2.0),(3,37,'掌握',1.0),
-- 产品经理 (4)
(4,34,'熟练',2.0),(4,35,'精通',3.0),(4,33,'掌握',1.5),(4,27,'了解',1.0),
-- 测试开发 (5)
(5,1,'熟练',2.0),(5,2,'掌握',1.5),(5,48,'精通',2.5),(5,15,'掌握',1.0),(5,20,'掌握',1.5),
-- 全栈开发 (6)
(6,1,'精通',3.0),(6,3,'精通',3.0),(6,7,'熟练',2.0),(6,10,'熟练',2.0),(6,15,'熟练',2.0),(6,16,'掌握',1.5),
-- 算法工程师 (7)
(7,2,'精通',3.0),(7,28,'精通',3.0),(7,29,'精通',3.0),(7,4,'掌握',1.0),(7,37,'掌握',1.5),
-- DevOps (8)
(8,21,'精通',3.0),(8,22,'熟练',2.5),(8,23,'精通',2.5),(8,24,'熟练',2.0),(8,26,'熟练',2.0),
-- Android开发 (9)
(9,1,'精通',2.5),(9,5,'掌握',1.5),(9,27,'熟练',2.0),(9,30,'熟练',2.0),(9,15,'掌握',1.0),
-- iOS开发 (10)
(10,6,'精通',2.5),(10,27,'熟练',2.0),(10,30,'熟练',2.0),
-- Golang后端 (11)
(11,5,'精通',3.0),(11,16,'熟练',2.0),(11,21,'熟练',2.0),(11,22,'掌握',1.5),(11,15,'熟练',2.0),
-- 安全工程师 (12)
(12,23,'精通',2.5),(12,2,'熟练',2.0),(12,28,'掌握',1.5),(12,21,'掌握',1.5),
-- UI/UX设计 (13)
(13,36,'掌握',1.0),(13,10,'了解',0.5),(13,34,'熟练',2.0),(13,35,'精通',2.5),
-- 技术项目经理 (14)
(14,34,'精通',2.5),(14,35,'精通',2.5),(14,31,'熟练',2.0),(14,33,'熟练',2.0),(14,27,'掌握',1.5),
-- DBA (15)
(15,15,'精通',3.0),(15,16,'精通',2.5),(15,18,'熟练',2.0),(15,23,'熟练',2.0),(15,32,'精通',2.5),
-- 嵌入式开发 (16)
(16,1,'掌握',1.5),(16,6,'精通',3.0),(16,27,'熟练',2.0),(16,23,'熟练',2.0),
-- 数据分析师 (17)
(17,2,'熟练',2.5),(17,15,'熟练',2.0),(17,37,'掌握',1.5),(17,34,'掌握',1.5),
-- C++系统开发 (18)
(18,6,'精通',3.0),(18,23,'熟练',2.0),(18,27,'精通',2.5),(18,28,'精通',2.5),(18,29,'熟练',2.0),
-- 区块链开发 (19)
(19,2,'熟练',2.0),(19,5,'熟练',2.0),(19,6,'掌握',1.5),(19,28,'精通',2.5),(19,29,'熟练',2.0),
-- AI应用开发 (20)
(20,2,'精通',3.0),(20,7,'掌握',1.5),(20,28,'熟练',2.0),(20,21,'掌握',1.5),
-- 软件测试 (21)
(21,1,'掌握',1.0),(21,2,'掌握',1.0),(21,48,'熟练',2.0),(21,49,'熟练',2.0),(21,34,'掌握',1.5),
-- 运维工程师 (22)
(22,23,'精通',3.0),(22,24,'熟练',2.0),(22,15,'掌握',1.5),(22,16,'掌握',1.5),(22,21,'熟练',2.0);

-- 测评题库 (50+道)
INSERT INTO `assessment_question` (`dimension`, `type`, `content`, `options`, `answer`, `score`, `difficulty`) VALUES
-- 编程能力 (10题)
('PROGRAMMING','SELECT','以下哪个不是面向对象编程的基本特征？','["继承","多态","封装","递归"]','D',5,'简单'),
('PROGRAMMING','SELECT','Java中，用于创建抽象类的关键字是？','["interface","abstract","static","final"]','B',5,'简单'),
('PROGRAMMING','SELECT','以下哪种数据结构是先进后出(LIFO)的？','["队列","栈","数组","链表"]','B',5,'简单'),
('PROGRAMMING','SELECT','在面向对象设计中，开闭原则的含义是什么？','["对扩展开放，对修改关闭","对修改开放，对扩展关闭","类应该只有一个职责","依赖应该倒置"]','A',5,'中等'),
('PROGRAMMING','SELECT','HashMap与Hashtable的主要区别是什么？','["HashMap是线程安全的","Hashtable允许null键","HashMap允许null键和null值","两者完全相同"]','C',5,'中等'),
('PROGRAMMING','SELECT','以下哪个SQL语句用于从表中删除所有行但保留表结构？','["DELETE FROM table","TRUNCATE TABLE table","DROP TABLE table","REMOVE FROM table"]','A',5,'简单'),
('PROGRAMMING','SELECT','RESTful API中，用于更新资源的HTTP方法是？','["GET","POST","PUT","DELETE"]','C',5,'简单'),
('PROGRAMMING','SELECT','以下哪个不是版本控制工具？','["Git","SVN","Mercurial","Docker"]','D',5,'简单'),
('PROGRAMMING','SELECT','线程安全问题通常由什么引起？','["共享资源的并发访问","内存不足","CPU使用率过高","磁盘空间不足"]','A',5,'中等'),
('PROGRAMMING','SELECT','以下哪种设计模式属于创建型模式？','["观察者模式","单例模式","适配器模式","装饰器模式"]','B',5,'中等'),
-- 逻辑推理 (10题)
('LOGIC','SELECT','观察数列：2, 6, 12, 20, ? , 下一个数字是什么？','["28","30","32","34"]','B',5,'中等'),
('LOGIC','SELECT','所有的猫都怕水，小花是猫，因此？','["小花不怕水","小花怕水","小花不是猫","以上都不对"]','B',5,'简单'),
('LOGIC','SELECT','如果A>B，B>C，那么以下哪个结论一定正确？','["A<C","A=C","A>C","无法确定"]','C',5,'简单'),
('LOGIC','SELECT','有5个连续的正整数，它们的和是100，最小的那个数是多少？','["18","19","20","21"]','A',5,'中等'),
('LOGIC','SELECT','以下哪个推理是正确的？','["如果下雨，地就会湿。地湿了，所以下雨了。","所有的鸟都会飞。企鹅是鸟，所以企鹅会飞。","所有的正方形都是矩形，所以矩形的性质正方形都有。","如果A则B。非B，所以非A。"]','D',5,'困难'),
('LOGIC','SELECT','找出不同的一个：','["苹果","香蕉","橘子","土豆"]','D',5,'简单'),
('LOGIC','SELECT','甲、乙、丙三人中有一人是小偷。甲说：我不是小偷；乙说：我是小偷；丙说：乙不是小偷。只有一个人说了真话，小偷是谁？','["甲","乙","丙","无法判断"]','A',5,'困难'),
('LOGIC','SELECT','一个瓶子和一个杯子的总价是11元，瓶子比杯子贵10元，杯子多少元？','["0.5元","1元","1.5元","2元"]','A',5,'中等'),
('LOGIC','SELECT','3个人3天用3桶水，9个人9天用多少桶水？','["9桶","18桶","27桶","81桶"]','C',5,'中等'),
('LOGIC','SELECT','以下哪个命题的逻辑错误是\"循环论证\"？','["因为大家都在用，所以它一定好","因为我说的对，所以我是对的","这事要么成，要么败","他成功了，因为他努力"]','B',5,'困难'),
-- 产品思维 (10题)
('PRODUCT','SELECT','产品经理的核心职责是什么？','["编写代码","发现并定义用户需求","测试产品","市场销售"]','B',5,'简单'),
('PRODUCT','SELECT','MVP（最小可行产品）的目的是？','["做出最完美的产品","用最小成本验证核心假设","拖延产品上线","减少团队人数"]','B',5,'简单'),
('PRODUCT','SELECT','在需求分析中，MoSCoW法则的M代表？','["More","Must-have","Maybe","Minimum"]','B',5,'中等'),
('PRODUCT','SELECT','以下哪个不是产品需求文档(PRD)应包含的内容？','["用户故事","功能描述","技术架构图","验收标准"]','C',5,'中等'),
('PRODUCT','SELECT','哪种方法最适合发现用户的真正痛点？','["头脑风暴","竞品分析","用户访谈","做问卷"]','C',5,'简单'),
('PRODUCT','SELECT','A/B测试的主要目的是？','["对比两个版本的效果","提高服务器性能","降低开发成本","加快开发速度"]','A',5,'简单'),
('PRODUCT','SELECT','以下哪个不是用户体验(UX)的关键要素？','["可用性","易学性","美观度","数据库性能"]','D',5,'中等'),
('PRODUCT','SELECT','KANO模型将需求分为哪几类？','["基本型、期望型、兴奋型","紧急、重要、次要","短期、中期、长期","技术型、业务型、体验型"]','A',5,'中等'),
('PRODUCT','SELECT','关于用户故事，以下描述正确的是？','["它是一种需求表述方式，格式为：作为...我想要...以便...","它是写给开发者的技术文档","它是用户体验设计图","它是项目进度计划表"]','A',5,'简单'),
('PRODUCT','SELECT','在产品数据分析中，\"转化率\"指的是？','["用户完成目标行为的比例","页面加载速度","用户增长速度","产品好评率"]','A',5,'简单'),
-- 技术素养 (10题)
('TECH','SELECT','HTTP状态码200表示？','["请求成功","资源已永久移动","未授权","服务器错误"]','A',5,'简单'),
('TECH','SELECT','TCP/IP协议中，TCP是面向什么连接的？','["无连接","面向连接","广播","多播"]','B',5,'简单'),
('TECH','SELECT','以下哪个不属于关系型数据库？','["MySQL","PostgreSQL","MongoDB","Oracle"]','C',5,'简单'),
('TECH','SELECT','Docker的核心概念不包括？','["镜像(Image)","容器(Container)","仓库(Registry)","虚拟机(Virtual Machine)"]','D',5,'中等'),
('TECH','SELECT','在CI/CD中，CI代表什么？','["持续集成","持续部署","代码审查","配置管理"]','A',5,'简单'),
('TECH','SELECT','CSRF攻击的全称是什么？','["跨站脚本攻击","跨站请求伪造","SQL注入","中间人攻击"]','B',5,'中等'),
('TECH','SELECT','以下哪个不是CAP理论中的要素？','["一致性","可用性","分区容错性","可扩展性"]','D',5,'中等'),
('TECH','SELECT','HTTPS使用的加密协议是？','["HTTP","TCP","SSL/TLS","DNS"]','C',5,'简单'),
('TECH','SELECT','Linux中，查看进程的命令是？','["cd","ls","ps","mkdir"]','C',5,'简单'),
('TECH','SELECT','以下哪种技术不是用于提高系统并发处理能力的？','["负载均衡","缓存","读写分离","数据加密"]','D',5,'中等'),
-- 沟通表达 (10题)
('COMMUNICATION','SELECT','在技术方案评审中，以下做法最合适的是？','["坚持自己的想法不妥协","直接否定他人的方案","用数据和案例说明方案的优缺点","避免表达观点随大流"]','C',5,'中等'),
('COMMUNICATION','SELECT','向上级汇报项目进度时，最应该关注什么？','["使用了哪些技术","项目进度和风险","团队成员表现","详细的代码实现"]','B',5,'简单'),
('COMMUNICATION','SELECT','收到用户反馈的Bug时，首先应该？','["直接修复代码","复现问题并确认","回复用户是正常现象","忽略该反馈"]','B',5,'简单'),
('COMMUNICATION','SELECT','以下哪种沟通方式最有利于解决团队冲突？','["回避","竞争","妥协","协作"]','D',5,'简单'),
('COMMUNICATION','SELECT','在进行技术分享演讲时，以下哪个做法最有效？','["大量堆砌代码","用生动案例引入主题","只讲理论不讲实践","尽可能多地展示知识点"]','B',5,'中等'),
('COMMUNICATION','SELECT','编写技术文档时，最重要的原则是？','["使用高深的术语","篇幅越长越好","清晰、准确、可读","只写给自己看"]','C',5,'简单'),
('COMMUNICATION','SELECT','收到不合理的需求变更时，最恰当的反应是？','["直接拒绝","置之不理","分析影响并沟通替代方案","全盘接受不质疑"]','C',5,'中等'),
('COMMUNICATION','SELECT','以下哪个不是有效倾听的表现？','["保持眼神交流","打断对方提出质疑","适时总结确认理解","保持开放心态"]','B',5,'简单'),
('COMMUNICATION','SELECT','在跨部门协作中，最重要的是？','["坚持部门利益最大化","建立共同目标和信任","避免与其他部门沟通","只完成自己部门的任务"]','B',5,'中等'),
('COMMUNICATION','SELECT','邮件沟通的基本原则不包括？','["主题清晰明确","内容简洁有重点","使用冒犯性语言","及时回复"]','C',5,'简单');

-- 学习资源库 (50+)
INSERT INTO `learning_resource` (`skill_id`, `stage`, `type`, `title`, `url`, `description`, `difficulty`, `estimated_hours`) VALUES
-- Java/BASIC (Skill 1)
(1,'BASIC','ARTICLE','Java入门教程 - 廖雪峰官方网站','https://www.liaoxuefeng.com/wiki/1252599548343744','覆盖Java SE核心知识，适合零基础入门','简单',10),
(1,'BASIC','VIDEO','尚硅谷Java基础全套教程','https://www.bilibili.com/video/BV1Kb411W75N','Java零基础入门含IDEA使用教学','简单',80),
(1,'FRAMEWORK','PROJECT','Spring Boot实战项目 - 博客系统','https://github.com/xxx/blog-springboot','完整的博客系统前后端实战','中等',20),
(1,'FRAMEWORK','ARTICLE','Spring Boot官方文档中文版','https://springdoc.cn/spring-boot/','Spring Boot框架完整参考文档','中等',15),
(1,'PROJECT','PROJECT','电商秒杀系统设计实战','https://github.com/xxx/seckill','高并发秒杀系统设计与实现','困难',30),
(1,'INTERVIEW','EXERCISE','Java面试题库 - LeetCode','https://leetcode.cn/problemset/all/?topic=java','LeetCode Java面试算法题库','中等',20),
-- Python (Skill 2)
(2,'BASIC','ARTICLE','Python官方入门教程','https://docs.python.org/zh-cn/3/tutorial/','Python官方中文入门文档','简单',10),
(2,'BASIC','VIDEO','黑马程序员Python入门教程','https://www.bilibili.com/video/BV1ex411x7Em','系统学习Python基础语法和常用库','简单',60),
(2,'FRAMEWORK','ARTICLE','Flask入门 - Flask中文文档','https://dormousehole.readthedocs.io/','Flask框架官方中文文档','中等',10),
(2,'PROJECT','PROJECT','数据分析项目实战 - 淘宝用户行为分析','https://github.com/xxx/taobao-analysis','使用Pandas/Matplotlib进行真实数据分析','中等',15),
(2,'INTERVIEW','ARTICLE','Python面试高频考点整理','https://github.com/kenwoodjw/python_interview_question','Python面试常见问题与答案','中等',8),
-- JavaScript (Skill 3)
(3,'BASIC','ARTICLE','MDN JavaScript教程','https://developer.mozilla.org/zh-CN/docs/Web/JavaScript','MDN官方JS参考文档','简单',15),
(3,'BASIC','VIDEO','尚硅谷JavaScript高级教程','https://www.bilibili.com/video/BV14s411E7qf','深入理解JS核心概念','简单',40),
(3,'FRAMEWORK','PROJECT','Vue.js实战项目 - 商城前台','https://github.com/xxx/vue-mall','电商前端项目完整实现','中等',25),
-- Vue.js (Skill 10)
(10,'BASIC','ARTICLE','Vue.js官方文档','https://cn.vuejs.org/guide/introduction.html','Vue3官方中文渐进式学习文档','简单',10),
(10,'BASIC','VIDEO','Vue3入门到实战教程 - 黑马程序员','https://www.bilibili.com/video/BV1HV4y1a7n4','Vue3组合式API+Pinia状态管理实战','简单',50),
(10,'INTERVIEW','ARTICLE','Vue.js面试题合集','https://github.com/febobo/web-interview/issues/11','前端面试中Vue高频考点','中等',5),
-- React (Skill 11)
(11,'BASIC','ARTICLE','React官方文档中文版','https://zh-hans.react.dev/','React官方中文教程','简单',12),
(11,'FRAMEWORK','PROJECT','React实战 - 后台管理系统','https://github.com/xxx/react-admin','基于Ant Design Pro的后台管理','中等',30),
-- MySQL (Skill 15)
(15,'BASIC','ARTICLE','MySQL基础教程 - 菜鸟教程','https://www.runoob.com/mysql/mysql-tutorial.html','MySQL入门到进阶教程','简单',8),
(15,'BASIC','VIDEO','尚硅谷MySQL数据库教程','https://www.bilibili.com/video/BV12b411K7Zu','MySQL基础到高级完整教程','简单',50),
(15,'FRAMEWORK','ARTICLE','MySQL性能优化实战指南','https://www.mysql.com/why-mysql/presentations/mysql-performance-tuning/','索引优化、SQL调优、架构设计','中等',10),
(15,'INTERVIEW','EXERCISE','SQL面试题50道','https://www.nowcoder.com/ta/sql-review','牛客网SQL实战练习题库','中等',15),
-- Redis (Skill 16)
(16,'BASIC','ARTICLE','Redis入门指南','https://www.redis.com.cn/tutorial.html','Redis基础概念与常用命令','简单',5),
(16,'FRAMEWORK','ARTICLE','Redis实战 - 缓存设计模式','https://redis.io/docs/manual/patterns/','缓存穿透、雪崩、击穿解决方案','中等',6),
-- Git (Skill 20)
(20,'BASIC','ARTICLE','Git学习 - 廖雪峰Git教程','https://www.liaoxuefeng.com/wiki/896043488029600','Git从入门到精通','简单',5),
(20,'BASIC','VIDEO','学会Git玩转GitHub','https://www.bilibili.com/video/BV1sJ411D7xN','Git基本操作与GitHub协作','简单',8),
-- Docker (Skill 21)
(21,'BASIC','ARTICLE','Docker从入门到实践','https://yeasy.gitbook.io/docker_practice/','系统学习Docker容器技术','简单',8),
(21,'FRAMEWORK','PROJECT','Docker实战 - 微服务容器化部署','https://github.com/docker/labs','Docker Compose编排微服务','中等',12),
-- Linux (Skill 23)
(23,'BASIC','ARTICLE','Linux常用命令大全','https://www.runoob.com/linux/linux-command-manual.html','Linux基础命令速查手册','简单',6),
(23,'BASIC','VIDEO','韩顺平一周学会Linux','https://www.bilibili.com/video/BV1Sv411r7vd','Linux快速入门实战教程','简单',30),
-- 数据结构 (Skill 27)
(27,'BASIC','VIDEO','浙江大学数据结构-陈越','https://www.icourse163.org/course/ZJU-93001','数据结构基础课含编程练习','中等',40),
(27,'FRAMEWORK','EXERCISE','LeetCode热题100','https://leetcode.cn/problem-list/2cktkvj/','高频算法面试题练习','中等',30),
(27,'INTERVIEW','ARTICLE','剑指Offer题解汇总','https://github.com/CyC2018/CS-Notes/blob/master/notes/剑指%20Offer%20题解%20-%20目录.md','数据结构与算法面试核心','困难',25),
-- 算法 (Skill 28)
(28,'BASIC','ARTICLE','算法图解 - 像小说一样有趣的算法书','https://github.com/egonSchiele/grokking_algorithms','通俗易懂学习常用算法','简单',8),
(28,'FRAMEWORK','EXERCISE','牛客网算法训练营','https://www.nowcoder.com/ta/algorithm-interview','算法面试高频题训练','中等',25),
(28,'INTERVIEW','ARTICLE','程序员面试算法宝典','https://github.com/geekxh/hello-algorithm','图解算法高频面试题','困难',20),
-- 系统设计 (Skill 31)
(31,'FRAMEWORK','ARTICLE','系统设计入门','https://github.com/donnemartin/system-design-primer/blob/master/README-zh-Hans.md','分布式系统设计学习指南','中等',15),
(31,'INTERVIEW','ARTICLE','后端系统设计面试指南','https://github.com/yangshun/tech-interview-handbook/tree/master/design','系统设计面试准备资料','困难',12),
-- 面向对象设计 (Skill 30)
(30,'FRAMEWORK','ARTICLE','设计模式 - 菜鸟教程','https://www.runoob.com/design-pattern/design-pattern-tutorial.html','23种设计模式详解','中等',10),
(30,'INTERVIEW','EXERCISE','Head First设计模式实战练习','https://github.com/bethrobson/Head-First-Design-Patterns','设计模式代码实践','中等',15),
-- 数据库设计 (Skill 32)
(32,'FRAMEWORK','ARTICLE','数据库设计三大范式详解','https://www.runoob.com/sql/sql-database-design.html','规范化数据库设计方法论','中等',5),
(32,'INTERVIEW','ARTICLE','数据库面试高频考点','https://github.com/CyC2018/CS-Notes/blob/master/notes/数据库系统原理.md','数据库原理面试题集合','中等',8),
-- RESTful API (Skill 26)
(26,'BASIC','ARTICLE','RESTful API设计最佳实践','https://restfulapi.net/','REST架构风格指南','简单',4),
(26,'FRAMEWORK','PROJECT','Spring Boot REST API实战','https://spring.io/guides/tutorials/rest/','使用Spring Boot构建RESTful服务','中等',10),
-- Spring Boot (Skill 7)
(7,'BASIC','VIDEO','Spring Boot2 教程 - 雷丰阳','https://www.bilibili.com/video/BV19K4y1L7MT','Spring Boot完整学习路线','中等',60),
(7,'FRAMEWORK','PROJECT','Spring Boot企业级项目实战','https://github.com/xxx/enterprise-project','企业级开发规范与最佳实践','困难',30),
(7,'INTERVIEW','ARTICLE','Spring面试题合集','https://github.com/Snailclimb/JavaGuide','Java后端面试核心知识点','中等',10),
-- MongoDB (Skill 17)
(17,'BASIC','ARTICLE','MongoDB快速入门','https://www.runoob.com/mongodb/mongodb-tutorial.html','NoSQL数据库MongoDB基础','简单',6),
-- Elasticsearch (Skill 19)
(19,'BASIC','ARTICLE','Elasticsearch权威指南','https://www.elastic.co/guide/cn/elasticsearch/guide/current/index.html','ES中文入门指导','中等',12),
-- 沟通协作 (Skill 35)
(35,'BASIC','ARTICLE','高效团队协作的5个原则','https://www.atlassian.com/team-playbook','团队协作方法论','简单',2),
(35,'INTERVIEW','ARTICLE','技术面试沟通技巧','https://github.com/yangshun/tech-interview-handbook','技术面试准备与沟通策略','简单',3);

-- FAQ数据 (50+)
INSERT INTO `faq` (`category`, `question`, `answer`, `keywords`, `sort_order`) VALUES
-- 平台使用
('PLATFORM','如何使用AI求职辅导平台？','您可以先注册账号，填写求职画像，然后依次使用能力测评、职业方向探索、岗位匹配、学习路径等功能。平台会为您生成个性化的学习计划。','使用,入门,引导,教程',1),
('PLATFORM','平台支持哪些角色？','平台支持三种角色：学生用户、企业HR和管理员。学生用户可以使用求职辅导全套功能，企业HR可以进行项目需求和候选人推荐，管理员负责系统管理。','角色,学生,HR,管理员',2),
('PLATFORM','忘记密码怎么办？','目前版本暂不支持自助找回密码，请联系平台管理员重置密码。管理员可在管理后台为用户重置密码。','密码,重置,找回',3),
('PLATFORM','如何修改个人信息？','登录后，在学生端的「求职画像」页面可以修改个人基本信息、教育背景、技能标签和目标意愿。','个人信息,修改,设置',4),
('PLATFORM','平台数据是否安全？','平台采用JWT认证机制保障账户安全，密码使用BCrypt加密存储，所有数据不会泄露给第三方。','安全,隐私,数据',5),
('PLATFORM','平台有手机App吗？','当前版本为Web版本，支持PC端和移动端浏览器访问。后续版本会考虑开发移动端App。','App,手机,移动端',6),
('PLATFORM','如何注销账号？','如需注销账号，请联系平台管理员。管理员在后台可以将您的账号设为禁用状态。','注销,删除,账号',7),
('PLATFORM','平台的使用费用是多少？','当前版本为免费版本，所有功能均可免费使用，不收取任何费用。','费用,免费,收费',8),
-- 岗位介绍
('POSITION','Java后端开发需要掌握哪些技能？','Java后端开发需要掌握：Java语言基础、Spring Boot框架、MySQL数据库、Redis缓存、Git版本控制、Linux基本命令、RESTful API设计、基本算法数据结构。','Java,后端,技能,要求',10),
('POSITION','前端开发工程师的发展前景如何？','前端开发是互联网行业核心岗位之一，随着Web技术的发展，前端岗位需求持续增长。需要掌握HTML/CSS/JavaScript基础、Vue或React框架、TypeScript等技能。','前端,前景,发展',11),
('POSITION','什么是全栈开发？','全栈开发指同时具备前端和后端开发能力的工程师。需要同时掌握前端技术(Vue/React)和后端技术(Spring Boot/Node.js)以及数据库设计等。','全栈,开发,定义',12),
('POSITION','数据工程师和算法工程师有什么区别？','数据工程师主要负责数据平台建设、数据清洗和数据管道搭建，偏工程方向；算法工程师主要负责机器学习模型研发、调优和部署，偏研究和算法方向。','数据,算法,区别,工程师',13),
('POSITION','产品经理需要会编程吗？','产品经理不一定要会编程，但具备一定的技术背景有助于与技术团队更高效地沟通。了解软件开发流程和基本技术概念是加分项。','产品经理,编程,技术',14),
('POSITION','测试工程师的职业发展路径是什么？','测试工程师可以从功能测试做起，逐步向自动化测试、性能测试、测试开发方向发展，也可以转向质量管理、项目管理等岗位。','测试,职业,发展',15),
('POSITION','云计算方向的就业前景？','云计算是当今IT行业最热门的方向之一，掌握Docker、Kubernetes等容器技术以及AWS/Azure/阿里云等云平台，就业前景非常好。','云计算,Docker,Kubernetes',16),
('POSITION','什么是DevOps工程师？','DevOps工程师负责打通开发(Dev)和运维(Ops)之间的壁垒，通过CI/CD流水线、自动化部署和监控来提高软件交付的效率和质量。','DevOps,CI/CD,运维',17),
('POSITION','转行做程序员需要做什么准备？','建议先确定想从事的方向（前端/后端/数据等），系统学习对应技术栈，做2-3个实战项目，刷算法面试题，准备技术面试。','转行,准备,程序员',18),
('POSITION','应届生去大厂还是创业公司？','大厂提供更多资源、规范流程和学习机会，适合积累经验；创业公司更灵活，个人成长空间大，适合快速成长。建议根据个人目标和阶段选择。','大厂,创业,选择,应届生',19),
-- 学习建议
('LEARNING','学习编程有什么好的方法？','建议：1）理论结合实践，边学边敲代码 2）从简单项目开始逐步进阶 3）多看优质开源项目代码 4）坚持每天写代码 5）参加技术社区交流 6）定期复习和总结。','学习方法,编程,建议',20),
('LEARNING','每天应该花多少时间学习？','建议初学者每天至少投入1-2小时，周末可以适当延长。关键是保持持续性，养成每天学习的习惯。','学习时间,每天,计划',21),
('LEARNING','前端和后端哪个更容易入门？','前端入门门槛相对较低，视觉效果直观，容易获得成就感；后端需要更多抽象思维和系统设计能力。两者各有特点，建议先尝试后选择适合的方向。','前端,后端,入门,难度',22),
('LEARNING','如何准备技术面试？','1）系统复习数据结构与算法 2）深入理解简历中的项目经验 3）准备系统设计题目 4）练习常见的编程语言面试题 5）模拟面试练习表达 6）了解目标公司的面试风格。','面试,准备,技术',23),
('LEARNING','算法题刷到多少道才够？','没有固定数量。建议刷LeetCode热题100+剑指Offer，重点理解解题思路而非死记硬背，保证常见题型能用最优解写出。','算法,刷题,数量',24),
('LEARNING','Git和GitHub有什么区别？','Git是分布式版本控制系统（工具），GitHub是基于Git的代码托管平台（网站）。类似Git的平台还有GitLab、Gitee等。','Git,GitHub,区别',25),
('LEARNING','学习到什么程度可以开始找工作？','建议达到能独立完成一个有一定复杂度项目的能力，掌握目标岗位的核心技能要求，算法面试题具备一定熟练度，就可以开始投递简历了。','找工作,准备程度',26),
('LEARNING','B站有哪些好的编程课程推荐？','推荐：尚硅谷Java/前端系列、黑马程序员Python系列、慕课网实战课程、极客时间专栏。选择课程时建议看评论区评价和播放量。','B站,课程,推荐,视频',27),
('LEARNING','有必要学习多种编程语言吗？','建议先精通一门语言深入理解编程思想，然后再学习其他语言拓展视野。不同语言有不同的应用场景，了解多门语言对职业发展有帮助。','编程语言,多种,精通',28),
('LEARNING','做项目时遇到Bug怎么解决？','建议：1）仔细阅读错误日志 2）在Stack Overflow/GitHub Issues搜索 3）使用调试工具逐步排查 4）简化问题复现 5）请教同学或导师 6）百度/谷歌搜索。','Bug,调试,解决问题',29),
-- 更多平台使用FAQ
('PLATFORM','能力测评有哪些维度？','平台测评包含五个维度：编程能力、逻辑推理、产品思维、技术素养和沟通表达，每个维度多道题目，全面评估您的职业能力水平。','测评,维度,评估',30),
('PLATFORM','AI职业方向探索的结果准确吗？','AI会根据您的求职画像、能力测评结果和兴趣偏好综合分析推荐。结果基于DeepSeek大模型，具有参考价值，但最终选择还需结合个人实际情况。','AI,方向,准确,探索',31),
('PLATFORM','简历优化功能支持哪些格式？','当前版本支持上传PDF和DOCX格式的简历文件，文件大小限制在5MB以内。','简历,格式,上传',32),
('PLATFORM','模拟面试是如何进行的？','选择目标岗位和面试类型后，AI会依次出题，您输入回答。AI会对每轮回答给予追问，最后生成五维度评估报告。','面试,模拟,流程',33),
('PLATFORM','学习路径是根据什么生成的？','学习路径根据能力差距分析结果自动生成，针对您的薄弱技能，按基础→框架→项目→面试四个阶段规划学习任务。','学习路径,生成,依据',34),
('PLATFORM','可以更换目标岗位吗？','可以随时在求职画像中修改目标岗位，平台会根据新的目标岗位重新进行匹配推荐和差距分析。','更换,修改,岗位',35),
('LEARNING','校招和社招有什么区别？','校招面向应届毕业生，更看重基础能力和学习潜力；社招面向有经验者，更看重项目经验和实际能力。校招通常有春招和秋招，时间固定。','校招,社招,区别',36),
('LEARNING','简历应该怎么写才能脱颖而出？','建议：1）用量化数据展示成果 2）突出与岗位匹配的技能 3）项目经历按STAR原则描述 4）简历保持一页，排版整洁 5）使用平台的AI简历优化功能获取专业建议。','简历,技巧,优秀',37),
('POSITION','远程办公的岗位有哪些？','远程办公岗位主要集中在：软件开发、UI/UX设计、内容创作等领域。近年来后端开发、前端开发和全栈开发远程岗位增长明显。','远程,办公,岗位',38),
('LEARNING','获得第一份开发工作后该怎么继续提升？','建议：1）深入业务领域知识 2）系统性学习架构设计 3）阅读优秀技术书籍 4）参与开源项目 5）参加技术分享会议 6）考取相关技术认证。','提升,进阶,第一份工作',39);

-- FAQ 客服相关
INSERT INTO `faq` (`category`, `question`, `answer`, `keywords`, `sort_order`) VALUES
('PLATFORM','如何联系平台客服？','您可以直接在智能客服页面提问，AI客服会为您解答常见问题。如需人工协助，请联系平台管理员。','客服,联系,帮助',40),
('PLATFORM','平台什么时候上新功能？','平台会持续迭代更新，具体更新计划请关注平台公告。如有功能建议，欢迎向管理员反馈。','更新,新功能,计划',41);
