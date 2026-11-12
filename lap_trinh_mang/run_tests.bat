@echo off
title Kiem Thu Tu Dong (Integration Test Suite)
echo ========================================================
echo   CHAY BO KIEM THU TICH HOP TU DONG TOAN HE THONG
echo ========================================================
java -Dfile.encoding=UTF-8 -cp ".;lib/*" test.SystemIntegrationTest
echo ========================================================
pause
