@echo off
title Chat Client GUI (Swing Messenger)
echo ========================================================
echo   KHOI CHAY CHAT CLIENT GIAO DIEN SWING
echo ========================================================
start "Chat Client GUI" java -Dfile.encoding=UTF-8 -cp ".;lib/*" client.ClientMain
