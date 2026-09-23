"""Versioned local backups, verified by reopening and hashing every archive member."""
import argparse,datetime as dt,hashlib,io,json,os,subprocess,tarfile,zipfile
from pathlib import Path

def sha(data):return hashlib.sha256(data).hexdigest()
def verify(path):
 with zipfile.ZipFile(path) as z:
  manifest=json.loads(z.read('manifest.json'))
  for name,digest in manifest['sha256'].items():
   if sha(z.read(name))!=digest:raise ValueError('Backup checksum mismatch: '+name)
  journal=json.loads(z.read('phone/files/journal.json'))
  if journal.get('version')!=1 or not isinstance(journal.get('meals'),list):raise ValueError('Invalid journal in backup')
 return manifest

def snapshot(adb,cache,destination,cloud=None,force=False):
 destination=Path(destination);destination.mkdir(parents=True,exist_ok=True)
 marker=destination/'latest.json';now=dt.datetime.now(dt.timezone.utc)
 if marker.exists() and not force:
  last=json.loads(marker.read_text(encoding='utf-8'))
  if (now-dt.datetime.fromisoformat(last['createdAt'])).total_seconds()<3600:return last
 devices=subprocess.check_output([adb,'devices'],text=True)
 if sum(line.endswith('\tdevice') for line in devices.splitlines())!=1:raise ValueError('Waiting for one authorized Android phone for backup')
 raw=subprocess.check_output([adb,'exec-out','run-as','com.simon.dailyrhythm','tar','-cf','-','files','shared_prefs'])
 members={}
 with tarfile.open(fileobj=io.BytesIO(raw)) as t:
  for m in t.getmembers():
   path=Path(m.name)
   if not m.isfile() or path.is_absolute() or '..' in path.parts:continue
   if path.name=='backup-status.json' or path.suffix in ['.pending','.tmp','.new']:continue
   members['phone/'+m.name]=t.extractfile(m).read()
 for p in Path(cache).rglob('*'):
  if p.is_file() and (p.suffix.lower()=='.fit' or p.name=='garmin-fit.json'):
   members['garmin/'+p.relative_to(cache).as_posix()]=p.read_bytes()
 if 'phone/files/journal.json' not in members:raise ValueError('No journal found; backup not marked successful')
 manifest={'schema':'daily-rhythm.backup.v1','createdAt':now.isoformat(),'sha256':{n:sha(v) for n,v in members.items()},
  'scope':'Daily Rhythm private app files, reminder settings, captured Health Connect records and cached Garmin FIT files. Unshared Garmin cloud data and other apps are not included.'}
 path=destination/('Daily-Rhythm-'+now.strftime('%Y-%m-%d_%H-%M-%S')+'.zip');temp=path.with_suffix('.pending')
 with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as z:
  for name,data in members.items():z.writestr(name,data)
  z.writestr('manifest.json',json.dumps(manifest,indent=2))
 verify(temp);temp.replace(path)
 status={'createdAt':manifest['createdAt'],'localPath':str(path),'localVerified':True,'files':len(members),'cloudStatus':'not_configured'}
 if cloud:
  folder=Path(cloud)
  try:
   if not folder.is_dir():raise ValueError('Chosen cloud folder is unavailable')
   target=folder/path.name;pending=target.with_suffix('.pending')
   pending.write_bytes(path.read_bytes());verify(pending);pending.replace(target)
   status.update(cloudStatus='folder_copy_verified',cloudPath=str(target))
  except Exception as exc:status.update(cloudStatus='failed',cloudError=str(exc))
 markerTemp=marker.with_suffix('.pending');markerTemp.write_text(json.dumps(status,indent=2),encoding='utf-8');markerTemp.replace(marker)
 return status

if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('--config',required=True);ap.add_argument('--force',action='store_true');args=ap.parse_args()
 cfg=json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
 try:
  result=snapshot(cfg['adb'],cfg['cache'],cfg['local'],cfg.get('cloud'),args.force)
 except Exception as exc:result={'localVerified':False,'error':str(exc)}
 ledger=Path(args.config).parent/'cloud-upload.json'
 if cfg.get('driveFolderId') and result.get('localVerified'):
  result['cloudStatus']='pending_drive_upload'
  if ledger.exists():
   cloud=json.loads(ledger.read_text(encoding='utf-8'))
   result['lastCloudVerifiedAt']=cloud.get('verifiedAt')
   if cloud.get('localPath')==result.get('localPath'):
    result.update(cloudStatus='drive_verified',cloudUrl=cloud.get('url'))
 output=Path(args.config).parent/'backup-status.json';output.write_text(json.dumps(result),encoding='utf-8')
 try:
  subprocess.run([cfg['adb'],'push',str(output),'/data/local/tmp/rhythm-backup-status.json'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  subprocess.run([cfg['adb'],'shell','run-as','com.simon.dailyrhythm','cp','/data/local/tmp/rhythm-backup-status.json','files/backup-status.pending'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  subprocess.run([cfg['adb'],'shell','run-as','com.simon.dailyrhythm','mv','files/backup-status.pending','files/backup-status.json'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  subprocess.run([cfg['adb'],'shell','rm','/data/local/tmp/rhythm-backup-status.json'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 except Exception:pass
 print(json.dumps(result))
