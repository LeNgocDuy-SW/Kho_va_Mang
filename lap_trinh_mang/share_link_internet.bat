@echo off
title Chat Server + Tao Link Internet
color 0A
cd /d "%~dp0"

echo =======================================================================
echo   KHOI DONG SERVER VA TAO DUONG LINK WEB CHAT INTERNET (ALL-IN-ONE)
echo =======================================================================
echo.

rem Kiem tra xem Server cong 8080 da chay chua
powershell -Command "if (!(Test-NetConnection -ComputerName 127.0.0.1 -Port 8080 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 1 } else { exit 0 }"

if %errorlevel% neq 0 (
    echo [1/2] Server chua bat, dang tu dong khoi dong Server Dashboard...
    start "" run_server.bat
    timeout /t 4 /nobreak > nul
    echo       Server da khoi dong thanh cong tren cong 5000 va 8080!
) else (
    echo [1/2] Server da duoc bat truoc do va dang hoat dong tot tren cong 8080!
)

echo.
echo [2/2] Dang ket noi Cloudflare Tunnel de tao duong link HTTPS...
echo.
echo =======================================================================
echo   CHU Y QUAN TRONG:
echo   - Duong link cua ban la dong co chua: .trycloudflare.com
echo     (Nam ben trong khung chu nhat o ben duoi)
echo   - Sau khi link hien ra, doi khoang 5 giay de Cloudflare ket noi xong
echo     (khi thay dong 'Registered tunnel connection' la link san sang 100%)
echo   - Copy link do gui qua Zalo / Messenger de chat ngay tren dien thoai / PC!
echo   - [Luu y]: Giu nguyen cua so nay chay ngam trong suot buoi chat.
echo =======================================================================
echo.

if exist "%~dp0cloudflared.exe" (
    "%~dp0cloudflared.exe" tunnel --url http://127.0.0.1:8080
) else (
    ssh -p 443 -o StrictHostKeyChecking=no -R0:localhost:8080 a.pinggy.io
)

pause
