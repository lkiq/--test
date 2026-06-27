@echo off
chcp 65001 > nul
title AI求职平台 - 后端服务

set MYSQL_PASSWORD=123456
set REDIS_PASSWORD=
set JWT_SECRET=career-platform-secret-key-2026
set DEEPSEEK_API_KEY=sk-2ddff53c02ff4448a4172b64f26ea784

cd /d "%~dp0backend"
echo ============================================
echo   正在启动后端服务 (端口 8080)...
echo ============================================
echo.
java -jar target\career-platform-1.0.0.jar --spring.profiles.active=dev
pause
