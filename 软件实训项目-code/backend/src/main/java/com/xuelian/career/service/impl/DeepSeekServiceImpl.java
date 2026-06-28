package com.xuelian.career.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.config.DeepSeekConfig;
import com.xuelian.career.entity.AiCallLog;
import com.xuelian.career.mapper.AiCallLogMapper;
import com.xuelian.career.service.DeepSeekService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

/**
 * DeepSeek API 服务实现类
 * 封装 API 调用、JSON 解析、Redis 缓存和降级处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekServiceImpl implements DeepSeekService {

    private final DeepSeekConfig deepSeekConfig;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiCallLogMapper aiCallLogMapper;
    private final ObjectMapper objectMapper;

    /** Redis 缓存键前缀 */
    private static final String AI_CACHE_PREFIX = "ai:cache:";

    /** API 可用状态缓存键 */
    private static final String AI_AVAILABLE_KEY = "ai:available";

    /** AI 调用独立线程池（10 线程，daemon，隔离 Tomcat 线程） */
    private final ExecutorService aiCallExecutor = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "ai-call-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * 调用 DeepSeek API（无缓存）- 向后兼容，委托新重载方法
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return API 返回的原始文本
     */
    @Override
    public String callAPI(String systemPrompt, String userPrompt) {
        return callAPI(systemPrompt, userPrompt, 5000L, 512);
    }

    /**
     * 调用 AI API（带超时和 max_tokens 配置）
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param timeoutMs    超时时间（毫秒）
     * @param maxTokens    最大生成 token 数
     * @return API 返回的原始文本
     */
    @Override
    public String callAPI(String systemPrompt, String userPrompt, long timeoutMs, int maxTokens) {
        return doCallAPI(systemPrompt, userPrompt, timeoutMs, maxTokens, 0.3);
    }

    /**
     * 带 Redis 缓存的 API 调用 - 向后兼容，委托新重载方法
     * 先查缓存 → 命中则返回 → 未命中则调 API → 结果存缓存
     * @param cacheKey     缓存键
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param ttlSeconds   缓存秒数（null 则使用默认值）
     * @return API 返回的原始文本
     */
    @Override
    public String callAPIWithCache(String cacheKey, String systemPrompt, String userPrompt, Long ttlSeconds) {
        long ttl = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : deepSeekConfig.getCacheTtl();
        return callAPIWithCache(cacheKey, systemPrompt, userPrompt, ttl, 512);
    }

    /**
     * 带 cache 的 AI 调用（带超时和 max_tokens 配置）
     * 先查缓存 → 命中则返回 → 未命中则调 API → 结果存缓存
     * @param cacheKey     缓存键
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param ttlSeconds   缓存秒数（兼作超时控制的 TTL）
     * @param maxTokens    最大生成 token 数
     * @return API 返回的原始文本
     */
    @Override
    public String callAPIWithCache(String cacheKey, String systemPrompt, String userPrompt, long ttlSeconds, int maxTokens) {
        String redisKey = AI_CACHE_PREFIX + cacheKey;
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            log.info("AI 缓存命中: cacheKey={}", cacheKey);
            return cached.toString();
        }
        String result = doCallAPI(systemPrompt, userPrompt, 5000L, maxTokens, 0.3);
        redisTemplate.opsForValue().set(redisKey, result, ttlSeconds, TimeUnit.SECONDS);
        return result;
    }

    /**
     * 核心 AI 调用逻辑（私有方法）
     * 构建 HTTP 请求并通过独立线程池异步发送，支持超时控制和异常隔离
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param timeoutMs    超时时间（毫秒）- 用于日志记录与异步等待
     * @param maxTokens    最大生成 token 数
     * @param temperature  采样温度（结构化 0.3 / 对话 0.6）
     * @return AI 返回内容
     */
    private String doCallAPI(String systemPrompt, String userPrompt, long timeoutMs, int maxTokens, double temperature) {
        long startTime = System.currentTimeMillis();
        AiCallLog callLog = new AiCallLog();
        callLog.setScene("AI_CALL");
        callLog.setPromptSummary(truncate(userPrompt, 200));
        callLog.setRequestHash(sha256(userPrompt));
        callLog.setCreatedAt(java.time.LocalDateTime.now());

        try {
            // 构建请求
            String apiKey = deepSeekConfig.getApiKey();
            log.info("DeepSeek API Key 状态: exists={}, startsWith={}, length={}",
                    apiKey != null, apiKey != null && apiKey.length() > 5 ? apiKey.substring(0, 5) : "null",
                    apiKey != null ? apiKey.length() : 0);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("Authorization", "Bearer " + apiKey.trim());
            } else {
                log.error("DeepSeek API Key 为空，无法调用 API");
                throw new RuntimeException("AI 服务未配置 API Key");
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", deepSeekConfig.getModel());

            // 消息列表
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new LinkedHashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);

            Map<String, String> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("调用 DeepSeek API: model={}, promptLength={}", deepSeekConfig.getModel(), userPrompt.length());

            // 异步发送请求（带超时控制，隔离 Tomcat 线程）
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                ResponseEntity<Map> response = restTemplate.exchange(
                        deepSeekConfig.getApiUrl(),
                        HttpMethod.POST,
                        entity,
                        Map.class
                );
                return extractContent(response.getBody());
            }, aiCallExecutor);

            String content;
            try {
                content = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RestClientException("AI 调用超时: " + timeoutMs + "ms");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RestClientException) throw (RestClientException) cause;
                throw new RestClientException("AI 调用异常: " + cause.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RestClientException("AI 调用被中断");
            }

            long duration = System.currentTimeMillis() - startTime;

            log.info("DeepSeek API 调用成功: duration={}ms, responseLength={}", duration,
                    content != null ? content.length() : 0);

            // 记录成功日志
            callLog.setResponseSource("AI");
            callLog.setStatus("SUCCESS");
            callLog.setDurationMs(duration);
            saveCallLog(callLog);

            // 标记 API 可用
            redisTemplate.opsForValue().set(AI_AVAILABLE_KEY, true, 60, TimeUnit.SECONDS);

            return content;

        } catch (RestClientException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("DeepSeek API 调用失败: {}", e.getMessage());

            callLog.setResponseSource("FALLBACK");
            callLog.setStatus("FAILED");
            callLog.setDurationMs(duration);
            callLog.setErrorMessage(truncate(e.getMessage(), 500));
            callLog.setFallbackReason("API不可用: " + e.getMessage());
            saveCallLog(callLog);

            // 标记 API 不可用
            redisTemplate.opsForValue().set(AI_AVAILABLE_KEY, false, 60, TimeUnit.SECONDS);

            throw new RuntimeException("AI 服务暂时不可用，请稍后重试", e);
        }
    }

    /**
     * 解析 API 返回的 JSON 字符串
     */
    @Override
    public Map<String, Object> parseJSONResponse(String response) {
        try {
            // 可能被 markdown 代码块包裹，提取 JSON 内容
            String json = extractJSON(response);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查 API 是否可用
     */
    @Override
    public boolean isAvailable() {
        Object available = redisTemplate.opsForValue().get(AI_AVAILABLE_KEY);
        if (available instanceof Boolean) {
            return (Boolean) available;
        }
        // 未缓存时默认可用的
        return true;
    }

    /**
     * 从 DeepSeek API 响应中提取文本内容
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> responseBody) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.warn("解析 API 响应格式异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从响应文本中提取 JSON 字符串（去除 markdown 包裹）
     */
    private String extractJSON(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        // 去除 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        return trimmed;
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * 计算字符串 SHA-256 哈希（用于缓存去重）
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 保存 AI 调用日志到数据库
     */
    private void saveCallLog(AiCallLog callLog) {
        try {
            aiCallLogMapper.insert(callLog);
        } catch (Exception e) {
            log.warn("保存 AI 调用日志失败: {}", e.getMessage());
        }
    }

    /**
     * 销毁 AI 调用线程池
     */
    @PreDestroy
    public void shutdown() {
        aiCallExecutor.shutdown();
        try {
            if (!aiCallExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                aiCallExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aiCallExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
