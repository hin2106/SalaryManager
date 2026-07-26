@echo off
cd /d "%~dp0"
title Luong Viet
mvn -q javafx:run
if errorlevel 1 pause
pause