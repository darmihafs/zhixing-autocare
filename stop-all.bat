@echo off
setlocal
cd /d "%~dp0"
title HongXing Car Wash - Stop Services

rem Stop only the 8 java processes whose command line matches
rem -jar ...target/<service>-1.0.0.jar of this project.
rem Other Java processes, frontend Vite and Docker middleware are untouched.
powershell -NoProfile -Command "$svcs=@('gateway','user-service','product-service','shop-service','order-service','pay-service','review-service','ai-service'); $found=@(); Get-CimInstance Win32_Process -Filter 'name=''java.exe'' or name=''javaw.exe''' | ForEach-Object { $cl=$_.CommandLine; if($cl){ foreach($s in $svcs){ if($cl -match ('-jar\s+\S*target[\\/]'+$s+'-1\.0\.0\.jar')){ $found+=$_; break } } } }; if($found.Count -eq 0){ Write-Host 'No project service process found' } else { foreach($p in $found){ $desc=$p.CommandLine; if($desc.Length -gt 140){ $desc=$desc.Substring(0,140)+'...' }; Write-Host ('Stopping PID='+$p.ProcessId+'  '+$desc); Stop-Process -Id $p.ProcessId -Force } }; Start-Sleep -Seconds 2; foreach($port in 8080..8087){ $c=Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue; if($c){ Write-Host ('[WARN] port '+$port+' still listening') } else { Write-Host ('[OK] port '+$port+' released') } }"

echo Done. To also stop frontend/middleware: close the Vite terminal and run "docker compose stop".
endlocal