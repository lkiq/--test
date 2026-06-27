@echo off
D:\Mysql\bin\mysql -u root -p123456 -e "SHOW DATABASES;" > D:\mysql-runtime\mysql_test.log 2>&1
echo DONE >> D:\mysql-runtime\mysql_test.log
