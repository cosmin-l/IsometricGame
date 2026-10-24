@echo off
rem Build and run Depths of Tristfall
cd /d "%~dp0"
if exist out rmdir /s /q out
javac -d out src\game\*.java || exit /b 1
java -cp out game.Main
