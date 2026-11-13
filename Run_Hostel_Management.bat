@echo off
setlocal
cd /d "%~dp0"
title Hostel Management System

echo =============================================
echo       HOSTEL MANAGEMENT SYSTEM
echo =============================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo Java is not installed or is not in PATH.
    echo Install Java 17 or newer and run this file again.
    pause
    exit /b 1
)

echo Starting application...
java -jar HostelManagementSystem.jar

if errorlevel 1 (
    echo.
    echo The application could not be started.
    pause
)
endlocal
