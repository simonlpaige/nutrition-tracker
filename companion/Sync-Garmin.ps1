param([switch]$Loop)
$ErrorActionPreference='Stop'
$rhythmPython=Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
$rhythmAdb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$rhythmCache=Join-Path (Split-Path -Parent $PSScriptRoot) 'garmin-cache'
$rhythmOutput=Join-Path $rhythmCache 'garmin-fit.json'
$rhythmStop=Join-Path $rhythmCache 'stop-sync'
$rhythmLog=Join-Path $rhythmCache 'sync-status.json'
New-Item -ItemType Directory -Force -Path $rhythmCache | Out-Null
$rhythmMutex=[Threading.Mutex]::new($false,'Local\DailyRhythmGarminSync')
if(-not $rhythmMutex.WaitOne(0)){Write-Output 'Garmin sync is already running';exit 0}
function Sync-RhythmWatch {
 $rhythmShell=New-Object -ComObject Shell.Application
 $rhythmWatches=@($rhythmShell.NameSpace(17).Items() | Where-Object { $_.Name -match '^fenix' })
 if($rhythmWatches.Count-ne 1){return 'Waiting for one Fenix watch over USB'}
 $rhythmStorage=@($rhythmWatches[0].GetFolder.Items() | Where-Object { $_.Name-eq 'Internal Storage' })
 if($rhythmStorage.Count-ne 1){return 'Watch storage is unavailable'}
 $rhythmGarmin=@($rhythmStorage[0].GetFolder.Items() | Where-Object { $_.Name-eq 'GARMIN' })
 if($rhythmGarmin.Count-ne 1){return 'Garmin folder is unavailable'}
 $rhythmFolders=@($rhythmGarmin[0].GetFolder.Items())
 foreach($rhythmName in @('Monitor','Sleep','HRVStatus','Metrics','SkinTemp','Activity')) {
  $rhythmFolder=@($rhythmFolders | Where-Object { $_.Name-eq $rhythmName })
  if($rhythmFolder.Count-ne 1){continue}
  $rhythmDest=(New-Item -ItemType Directory -Force -Path (Join-Path $rhythmCache $rhythmName)).FullName
  foreach($rhythmItem in @($rhythmFolder[0].GetFolder.Items())) {
   if($rhythmItem.IsFolder){continue}
   $rhythmFileName=$rhythmItem.Name
   if(-not $rhythmFileName.EndsWith('.FIT',[System.StringComparison]::OrdinalIgnoreCase)){$rhythmFileName+='.FIT'}
   if($rhythmFileName.IndexOfAny([IO.Path]::GetInvalidFileNameChars())-ge 0){continue}
   $rhythmLocal=Join-Path $rhythmDest $rhythmFileName
   if($rhythmName-ne 'Monitor' -and (Test-Path -LiteralPath $rhythmLocal)){continue}
   $rhythmShell.NameSpace($rhythmDest).CopyHere($rhythmItem,1556)
   $rhythmDeadline=[DateTime]::UtcNow.AddSeconds(20)
   do {Start-Sleep -Milliseconds 200} while(-not(Test-Path -LiteralPath $rhythmLocal)-and [DateTime]::UtcNow-lt $rhythmDeadline)
  }
 }
 Start-Sleep -Seconds 2
 & $rhythmPython (Join-Path $PSScriptRoot 'decode_fit.py') $rhythmCache $rhythmOutput | Out-Null
 if($LASTEXITCODE-ne 0){throw 'FIT decoding failed'}
 $rhythmDevices=@((& $rhythmAdb devices) | Where-Object { $_ -match '\sdevice$' })
 if($rhythmDevices.Count-ne 1){return 'Watch imported locally; waiting for one authorized Android phone'}
 & $rhythmAdb push $rhythmOutput /data/local/tmp/daily-rhythm-fit.json | Out-Null
 if($LASTEXITCODE-ne 0){throw 'Could not send import to phone'}
 & $rhythmAdb shell run-as com.simon.dailyrhythm cp /data/local/tmp/daily-rhythm-fit.json files/garmin-fit.pending | Out-Null
 if($LASTEXITCODE-ne 0){throw 'Daily Rhythm must be installed on this phone'}
 & $rhythmAdb shell run-as com.simon.dailyrhythm chmod 600 files/garmin-fit.pending | Out-Null
 if($LASTEXITCODE-ne 0){throw 'Could not protect phone import'}
 & $rhythmAdb shell run-as com.simon.dailyrhythm mv files/garmin-fit.pending files/garmin-fit.json | Out-Null
 if($LASTEXITCODE-ne 0){throw 'Could not finish phone import'}
 & $rhythmAdb shell rm /data/local/tmp/daily-rhythm-fit.json | Out-Null
 return 'Imported watch files to Daily Rhythm; refresh or reopen the app'
}
do {
 try {$rhythmMessage=Sync-RhythmWatch} catch {$rhythmMessage=$_.Exception.Message}
 $rhythmBackupConfig=Join-Path $PSScriptRoot 'backup-config.json'
 if(Test-Path -LiteralPath $rhythmBackupConfig){try{& $rhythmPython (Join-Path $PSScriptRoot 'backup_health.py') --config $rhythmBackupConfig | Out-Null}catch{}}
 @{at=[DateTime]::UtcNow.ToString('o');status=$rhythmMessage;pid=$PID} | ConvertTo-Json | Set-Content -LiteralPath $rhythmLog -Encoding utf8
 if(-not $Loop){Write-Output $rhythmMessage;break}
 Start-Sleep -Seconds 60
} while(-not(Test-Path -LiteralPath $rhythmStop))
$rhythmMutex.ReleaseMutex()
