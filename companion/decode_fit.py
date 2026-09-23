"""Decode local watch files using Garmin's official SDK. Never transmits data."""
import argparse, datetime as dt, hashlib, json, math
from pathlib import Path
from garmin_fit_sdk import Decoder, Stream

def stamp(value):
    if not isinstance(value,dt.datetime): return None
    if value.tzinfo is None: value=value.replace(tzinfo=dt.timezone.utc)
    return value.astimezone(dt.timezone.utc).isoformat().replace('+00:00','Z')

def decode(root, now=None):
    now=now or dt.datetime.now(dt.timezone.utc)
    cutoff=now-dt.timedelta(days=35)
    samples={}; sessions={}; issues=[]; files=0; unknown=set()
    mappings={
      'stress_level_mesgs':('stress_level_time','stress_level_value','stress',0,100),
      'respiration_rate_mesgs':('timestamp','respiration_rate','respiration',1,100),
      'spo2_data_mesgs':('timestamp','reading_spo2','spo2',1,100),
      'monitoring_mesgs':('timestamp','heart_rate','heart',20,250),
      'monitoring_hr_data_mesgs':('timestamp','resting_heart_rate','restingHeart',20,250),
      'hrv_value_mesgs':('timestamp','value','hrv',1,1000),
      'hrv_status_summary_mesgs':('timestamp','last_night_average','hrvNight',1,1000),
    }
    for p in sorted(Path(root).rglob('*.FIT')):
        if p.stat().st_size>30*1024*1024: issues.append(p.name+': file too large');continue
        try:
            decoder=Decoder(Stream.from_file(str(p)))
            if not decoder.is_fit() or not decoder.check_integrity():
                issues.append(p.name+': incomplete or invalid FIT file');continue
            # Integrity checking consumes the stream; decode from a fresh stream.
            data,errors=Decoder(Stream.from_file(str(p))).read()
            if errors: issues.append(p.name+': decoder rejected file');continue
            files+=1
            unknown.update(str(k) for k in data if isinstance(k,int) or str(k).isdigit())
            for group,(timefield,valuefield,kind,low,high) in mappings.items():
                for msg in data.get(group,[]):
                    at=msg.get(timefield);value=msg.get(valuefield)
                    if not isinstance(at,dt.datetime) or at<cutoff or at>now+dt.timedelta(minutes=5):continue
                    if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or not low<=value<=high:continue
                    iso=stamp(at);row={'at':iso,'kind':kind,'value':round(value,3)}
                    if kind=='spo2':
                        row['mode']=str(msg.get('mode','unknown'));row['confidence']=msg.get('reading_confidence')
                    samples[(iso,kind)]=row
            for msg in data.get('session_mesgs',[]):
                start=msg.get('start_time');end=msg.get('timestamp')
                if not isinstance(start,dt.datetime) or not isinstance(end,dt.datetime) or end<cutoff:continue
                row={'startUtc':stamp(start),'endUtc':stamp(end),'sport':str(msg.get('sport','unknown'))}
                for key in ['total_timer_time','total_elapsed_time','total_distance','total_calories','avg_heart_rate','max_heart_rate','total_ascent','total_descent']:
                    v=msg.get(key)
                    if isinstance(v,(int,float)) and math.isfinite(v) and v>=0:row[key]=v
                sessions[(row['startUtc'],row['sport'])]=row
        except Exception:
            issues.append(p.name+': could not decode')
    rows=sorted(samples.values(),key=lambda x:(x['at'],x['kind']))
    return {'schema':'daily-rhythm.garmin-fit.v1','importedAt':stamp(now),'source':'Garmin watch USB / official FIT SDK',
        'samples':rows,'activities':list(sessions.values()),'filesRead':files,'issues':issues[:30],
        'unknownMessageTypes':sorted(unknown),'coverageStart':rows[0]['at'] if rows else None,'coverageEnd':rows[-1]['at'] if rows else None,
        'limitations':['Only files still present on the watch or in the local cache are available.','Negative stress/respiration status codes are excluded, not treated as measurements.','Undocumented fields including possible Body Battery fields are not guessed.','Samples are irregular; averages are sample means, not time-weighted daily Garmin scores.','This is a USB import snapshot, not a live Garmin Connect cloud connection.'],
        'units':{'stress':'Garmin stress score 0–100','respiration':'breaths/min','spo2':'percent','heart':'bpm','restingHeart':'bpm','hrv':'ms','hrvNight':'ms'}}

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('cache');ap.add_argument('output');a=ap.parse_args()
    report=decode(a.cache);path=Path(a.output);temp=path.with_suffix('.tmp');temp.write_text(json.dumps(report,separators=(',',':')),encoding='utf-8');temp.replace(path)
    print(json.dumps({'filesRead':report['filesRead'],'sampleCount':len(report['samples']),'activityCount':len(report['activities']),'issues':report['issues']}))
