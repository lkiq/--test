package com.xuelian.career.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.interceptor.JwtInterceptor;
import com.xuelian.career.interceptor.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

/**
 * Web MVC 配置 - 注册 CORS 跨域、JWT/角色拦截器、UTF-8 消息转换器
 * （HTTP 编码由 application.yml 的 server.servlet.encoding 统一控制）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RoleInterceptor roleInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor, RoleInterceptor roleInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.roleInterceptor = roleInterceptor;
    }

    /**
     * 配置 HTTP 消息转换器，确保 JSON/字符串响应使用 UTF-8 编码
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // String 消息转换器 - UTF-8
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        converters.add(stringConverter);

        // Jackson JSON 转换器 - UTF-8 + 时区设置
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        converters.add(jacksonConverter);
    }

    /**
     * 配置 CORS 跨域，允许前端开发服务器（localhost:5173）访问
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册拦截器：
     * - JwtInterceptor：对所有 /api/** 校验 Token（排除认证接口）
     * - RoleInterceptor：对需要角色的接口校验权限
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/customer-service/faqs"
                );

        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/admin/**", "/api/enterprise/**");
    }
}
