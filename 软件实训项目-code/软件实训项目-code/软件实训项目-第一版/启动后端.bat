@echo off
chcp 65001 > nul
title AI求职平台 - 后端服务

REM ========================================
REM 环境变量载入（优先 .env 文件）
REM 如未配置 .env，请在下方或系统环境中设置
REM ========================================
if exist "%~dp0.env" (
    for /F "tokens=1,2 delims==" %%A in (%~dp0.env) do (
        set %%A=%%B
    )
)

REM 默认值（开发环境，仅本地使用）
if "%MYSQL_PASSWORD%"=="" set MYSQL_PASSWORD=123456
if "%JWT_SECRET%"=="" set JWT_SECRET=career-platform-secret-key-2026

cd /d "%~dp0backend"
echo ============================================
echo   正在启动后端服务 (端口 8080)...
echo ============================================
echo.
java -jar target\career-platform-1.0.0.jar --spring.profiles.active=dev
pause
