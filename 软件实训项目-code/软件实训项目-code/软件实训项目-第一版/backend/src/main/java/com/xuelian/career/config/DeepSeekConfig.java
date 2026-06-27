package com.xuelian.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek API 配置属性 - 从 application.yml 读取 deepseek.* 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {
    /** API 密钥 */
    private String apiKey;
    /** API 地址 */
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";
    /** 模型名称 */
    private String model = "deepseek-chat";
    /** 超时时间（秒） */
    private int timeoutSeconds = 60;
    /** 最大重试次数 */
    private int maxRetries = 1;
    /** 缓存 TTL（秒） */
    private long cacheTtl = 3600;
}
