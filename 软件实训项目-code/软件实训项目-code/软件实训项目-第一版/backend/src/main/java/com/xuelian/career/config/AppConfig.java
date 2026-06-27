package com.xuelian.career.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 通用配置 - REST 客户端等
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate Bean - 用于调用 DeepSeek API 等外部 HTTP 服务
     * 配置连接/读取超时，防止外部 API 无响应时线程阻塞
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 连接超时 10 秒
        factory.setReadTimeout(30000);     // 读取超时 30 秒（与前端 axios 超时一致）
        return new RestTemplate(factory);
    }
}
