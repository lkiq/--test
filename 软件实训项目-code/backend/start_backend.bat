@echo off
chcp 65001 > nul
set MYSQL_PASSWORD=123456
set REDIS_PASSWORD=
set JWT_SECRET=career-platform-secret-key-2026
set DEEPSEEK_API_KEY=sk-d5f34155cb3641699e50c0759fada351
"C:\Users\26025\.codebuddycn\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64\bin\java.exe" -jar target\career-platform-1.0.0.jar --spring.profiles.active=dev > backend.log 2>&1
