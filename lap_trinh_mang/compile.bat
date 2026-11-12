@echo off
title Bien Dich Du An Chat Room
echo ========================================================
echo   DANG BIEN DICH DU AN MULTI-CLIENT CHAT SERVER
echo ========================================================
javac -encoding UTF-8 -cp ".;lib/*" common/*.java server/*.java server/db/*.java server/web/*.java server/ui/*.java client/*.java client/network/*.java client/ui/*.java test/*.java

if %errorlevel% equ 0 (
    echo.
    echo [THANH CONG] Da bien dich xong tat ca cac module khong co loi!
) else (
    echo.
    echo [THAT BAI] Qua trinh bien dich gap loi. Vui long kiem tra JDK!
)
echo ========================================================
pause
