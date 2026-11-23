@echo off
title Intervia Project Launcher
echo ====================================================
echo     KHOI DONG HE THONG PHONG VAN AI - INTERVIA
echo ====================================================
echo.

echo [1/2] Dang khoi dong Backend Spring Boot (Port 8000)...
if exist "%~dp0backend-java\target\backend-java-0.1.0-SNAPSHOT.jar" (
    start "Intervia Backend (Port 8000)" /D "%~dp0backend-java" cmd /k java -jar target\backend-java-0.1.0-SNAPSHOT.jar
) else (
    start "Intervia Backend (Port 8000)" /D "%~dp0backend-java" cmd /k mvnw.cmd spring-boot:run
)

echo [2/2] Dang khoi dong Frontend React (Port 5173)...
start "Intervia Frontend (Port 5173)" /D "%~dp0web" cmd /k npm.cmd run dev

echo.
echo ====================================================
echo   Web app:     http://localhost:5173
echo   Swagger API: http://localhost:8000/swagger-ui.html
echo ====================================================
timeout /t 5 /nobreak >nul
start "" "http://localhost:5173"
