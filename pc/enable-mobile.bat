@echo off
rem Limbus Archive - let the Android app reach the desktop app on this PC.
rem Forwards TCP 47822 (LAN / Tailscale only) to the desktop app's local server 127.0.0.1:47821.
rem Run once; the setting survives reboots. Undo with disable-mobile.bat.
net session >nul 2>&1
if errorlevel 1 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
set APP_PORT=47821
set RELAY_PORT=47822
sc config iphlpsvc start= auto >nul 2>&1
net start iphlpsvc >nul 2>&1
netsh interface portproxy delete v4tov4 listenport=%RELAY_PORT% listenaddress=0.0.0.0 >nul 2>&1
netsh interface portproxy add v4tov4 listenport=%RELAY_PORT% listenaddress=0.0.0.0 connectport=%APP_PORT% connectaddress=127.0.0.1
netsh advfirewall firewall delete rule name="Limbus Archive Mobile" >nul 2>&1
netsh advfirewall firewall add rule name="Limbus Archive Mobile" dir=in action=allow protocol=TCP localport=%RELAY_PORT% remoteip=localsubnet,100.64.0.0/10 profile=any >nul
echo.
echo  ==========================================================
echo   Limbus Archive mobile connection is ON  (port %RELAY_PORT%)
echo  ==========================================================
echo.
echo   Enter one of these PC IP addresses in the Android app:
echo.
powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | ForEach-Object { '     ' + $_.IPAddress + '   (' + $_.InterfaceAlias + ')' }"
echo.
echo   Keep LimbusArchive.exe running while you use the phone.
echo.
pause
