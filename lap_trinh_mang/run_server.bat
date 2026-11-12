@echo off
title Chat Server (Port 5000 - SQLite DB)
echo ========================================================
echo   KHOI CHAY CHAT SERVER (GIAO DIEN QUAN TRI VIEN)
echo ========================================================
java -Dfile.encoding=UTF-8 -cp ".;lib/*" server.ServerMain
pause
