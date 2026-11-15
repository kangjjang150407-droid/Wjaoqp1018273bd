@echo off
rem Limbus Archive - turn the Android connection off again.
net session >nul 2>&1
if errorlevel 1 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
netsh interface portproxy delete v4tov4 listenport=47822 listenaddress=0.0.0.0 >nul 2>&1
netsh advfirewall firewall delete rule name="Limbus Archive Mobile" >nul 2>&1
echo Limbus Archive mobile connection is OFF.
pause
