package com.xuelian.career.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 通用配置 - REST 客户端等
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate Bean - 使用 Java 17 原生 HttpClient，自带连接复用
     * 全局唯一实例，业务层通过 @Autowired 注入，禁止 new
     * 读取超时从 30 秒降至 6 秒（略大于业务 5 秒硬超时）
     * 连接超时 3 秒，底层异步线程池 10 线程
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))      // 连接超时 3 秒
                .executor(Executors.newFixedThreadPool(10)) // 底层异步线程池
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(6));      // 读取超时 6 秒（原 30 秒）

        return new RestTemplate(factory);
    }
}
