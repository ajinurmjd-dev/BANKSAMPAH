@echo off
set "MVN_PATH=target\tools\apache-maven-3.9.11\bin\mvn.cmd"
if exist "%MVN_PATH%" (
    echo Membuka Bank Sampah...
    "%MVN_PATH%" exec:java
) else (
    echo Alat build tidak ditemukan. Jalankan perintah kompilasi dulu.
    pause
)
