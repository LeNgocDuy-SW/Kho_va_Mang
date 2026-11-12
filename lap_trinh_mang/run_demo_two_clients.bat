@echo off
title Demo Multi-Client Chat System
echo ========================================================
echo   KHOI DONG HE THONG TEST DEMO: 1 SERVER + 2 CLIENTS
echo ========================================================
echo [1/3] Khoi dong Server GUI...
start "Chat Server Host" java -Dfile.encoding=UTF-8 -cp ".;lib/*" server.ServerMain

timeout /t 2 /nobreak > nul

echo [2/3] Khoi dong Client 1 (Admin)...
start "Client 1 - Admin" java -Dfile.encoding=UTF-8 -cp ".;lib/*" client.ClientMain
 
timeout /t 1 /nobreak > nul

echo [3/3] Khoi dong Client 2 (User)...
start "Client 2 - User" java -Dfile.encoding=UTF-8 -cp ".;lib/*" client.ClientMain

echo.
echo ========================================================
echo   HE THONG TEST DA KHOI CHAY HOAN TAT!
echo   - Tai khoan Admin: admin / admin123
echo   - Tai khoan User:  user1 / 123456
echo ========================================================
