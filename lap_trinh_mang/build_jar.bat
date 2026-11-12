@echo off
title Dong Goi File JAR Cho Du An Chat
echo ========================================================
echo   DANG TAO FILE DONG GOI EXECUTABLE JAR CHO SERVER ^& CLIENT
echo ========================================================

rem 1. Bien dich truoc
javac -encoding UTF-8 -cp ".;lib/*" common/*.java server/*.java server/db/*.java server/web/*.java server/ui/*.java client/*.java client/network/*.java client/ui/*.java

echo.
echo [1/2] Dang dong goi chat_client.jar...
jar cfe chat_client.jar client.ClientMain client/*.class client/network/*.class client/ui/*.class common/*.class

echo.
echo [2/2] Dang dong goi chat_server.jar...
echo Main-Class: server.ServerMain > server_manifest.txt
echo Class-Path: lib/sqlite-jdbc.jar lib/slf4j-api.jar lib/slf4j-simple.jar >> server_manifest.txt
echo. >> server_manifest.txt

jar cfm chat_server.jar server_manifest.txt server/*.class server/db/*.class server/web/*.class server/ui/*.class common/*.class
if exist server_manifest.txt del server_manifest.txt

echo.
echo ========================================================
echo [HOAN TAT] Da tao thanh cong:
echo  1. chat_client.jar (Gui cho ban be hoac may khac de chay)
echo  2. chat_server.jar (May chu trung tam)
echo ========================================================
pause
