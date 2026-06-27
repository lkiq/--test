@echo off
chcp 65001 > nul
title AI求职平台 - 后端服务

set MYSQL_PASSWORD=123456
set REDIS_PASSWORD=
set JWT_SECRET=career-platform-secret-key-2026
set DEEPSEEK_API_KEY=sk-2dd53ffc02ff4448a4172b64f26ea784

echo ============================================
echo   正在启动后端服务 (端口 8080)...
echo ============================================
echo.

"C:\Users\26025\.jdks\corretto-17.0.13\bin\java.exe" -jar "%~dp0backend\target\career-platform-1.0.0.jar" --spring.profiles.active=dev
pause
