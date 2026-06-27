package com.xuelian.career.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.config.DeepSeekConfig;
import com.xuelian.career.entity.AiCallLog;
import com.xuelian.career.mapper.AiCallLogMapper;
import com.xuelian.career.service.DeepSeekService;
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
import java.util.concurrent.TimeUnit;

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

    /**
     * 调用 DeepSeek API（无缓存）
     */
    @Override
    public String callAPI(String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        AiCallLog callLog = new AiCallLog();
        callLog.setScene("AI_CALL");
        callLog.setPromptSummary(truncate(userPrompt, 200));
        callLog.setRequestHash(sha256(userPrompt));
        callLog.setCreatedAt(java.time.LocalDateTime.now());

        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(deepSeekConfig.getApiKey());

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
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 4096);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("调用 DeepSeek API: model={}, promptLength={}", deepSeekConfig.getModel(), userPrompt.length());

            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                    deepSeekConfig.getApiUrl(),
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            long duration = System.currentTimeMillis() - startTime;

            // 解析响应
            String content = extractContent(response.getBody());
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
     * 带 Redis 缓存的 API 调用
     * 先查缓存 → 命中则返回 → 未命中则调 API → 结果存缓存
     */
    @Override
    public String callAPIWithCache(String cacheKey, String systemPrompt, String userPrompt, Long ttlSeconds) {
        String redisKey = AI_CACHE_PREFIX + cacheKey;

        // 查缓存
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            log.info("AI 缓存命中: cacheKey={}", cacheKey);
            return cached.toString();
        }

        // 调 API
        String result = callAPI(systemPrompt, userPrompt);

        // 存缓存
        long ttl = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : deepSeekConfig.getCacheTtl();
        redisTemplate.opsForValue().set(redisKey, result, ttl, TimeUnit.SECONDS);

        return result;
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
}
