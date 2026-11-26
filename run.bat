@echo off
rem Jalankan dari folder proyek:  run.bat        (tes: run.bat tes)
cd /d "%~dp0"
if exist out rmdir /s /q out
dir /s /b src\*.java > sumber.txt
javac -d out @sumber.txt
del sumber.txt
if "%1"=="tes" (java -cp out kasir.TestKasir) else (java -cp out kasir.Main)
