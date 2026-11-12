@echo off
title Chat Server CLI (Console Mode)
echo ========================================================
echo   KHOI CHAY CHAT SERVER (CHE DO DONG LENH CLI)
echo ========================================================
java -Dfile.encoding=UTF-8 -cp ".;lib/*" server.ServerMain --cli
pause
