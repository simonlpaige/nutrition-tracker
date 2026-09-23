/* Shared, dependency-free journal model. Used by Android and the browser preview. */
(function(root){
  'use strict';
  const B=typeof module!=='undefined'?require('./body-core.js'):root.RhythmBody;
  const AI=typeof module!=='undefined'?require('./ai-core.js'):root.RhythmAI;
  const nutrients=['calories','protein','carbs','fat','fiber','sugar','sodium'];
  const bristol=['Separate hard lumps','Lumpy, sausage-shaped','Sausage with surface cracks','Smooth, soft sausage','Soft blobs with clear edges','Fluffy pieces, mushy','Watery, no solid pieces'];
  const empty=()=>({version:1,meals:[],stools:[],noBowel:[],goals:{calories:null,protein:null,fiber:null},ai:AI.defaults(),body:B.normalize()});
  const dayOf=(d=new Date())=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
  const offsetDay=(day,n)=>{const d=new Date(day+'T12:00:00');d.setDate(d.getDate()+n);return dayOf(d);};
  const id=()=>globalThis.crypto?.randomUUID?.()||('r'+Date.now().toString(36)+Math.random().toString(36).slice(2));
  function date(s){if(typeof s!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(s)||!Number.isFinite(Date.parse(s+'T12:00:00Z'))||new Date(s+'T12:00:00Z').toISOString().slice(0,10)!==s)throw Error('Invalid calendar date.');return s;}
  function number(n,max=30000,nullable=true){if(n===null&&nullable)return null;if(typeof n!=='number'||!Number.isFinite(n)||n<0||n>max)throw Error('Enter a valid, nonnegative nutrition value.');return n;}
  function text(s,max=2000){if(typeof s!=='string'||s.length>max)throw Error('Text is missing or too long.');return s;}
  function oneOf(v,list){if(!list.includes(v))throw Error('An entry has an unsupported choice.');return v;}
  function base(x){if(!x||typeof x!=='object')throw Error('Invalid entry.');const key=text(x.id,100);if(!/^[\w-]+$/.test(key))throw Error('Invalid entry ID.');if(typeof x.at!=='string'||!Number.isFinite(Date.parse(x.at)))throw Error('Invalid entry time.');return {id:key,at:new Date(x.at).toISOString(),day:date(x.day),notes:text(x.notes||''),...(x.offsetMinutes!==undefined?{offsetMinutes:(typeof x.offsetMinutes==='number'&&Number.isFinite(x.offsetMinutes)&&Math.abs(x.offsetMinutes)<=840?x.offsetMinutes:undefined)}:{})};}
  function meal(x){const b=base(x);b.name=text(x.name,180).trim();if(!b.name)throw Error('Give your meal a name.');b.category=oneOf(x.category,['Breakfast','Lunch','Dinner','Snack']);b.portion=text(x.portion||'',150);b.servings=number(x.servings,100,false);if(b.servings<=0)throw Error('Servings must be greater than zero.');for(const n of nutrients)b[n]=number(x[n],n==='sodium'?100000:30000,true);if(x.estimate!==undefined)b.estimate=AI.estimate(x.estimate);b.ingredients=text(x.ingredients||'',3000);if(x.estimatedNutrients!==undefined){if(!Array.isArray(x.estimatedNutrients)||x.estimatedNutrients.some(k=>!nutrients.includes(k)))throw Error('Invalid estimated nutrients.');b.estimatedNutrients=[...new Set(x.estimatedNutrients)];}return b;}
  function stool(x){const b=base(x);b.type=number(x.type,7,false);if(!Number.isInteger(b.type)||b.type<1)throw Error('Choose a Bristol type from 1 to 7.');b.ease=oneOf(x.ease,['Easy','Some straining','Difficult']);b.urgency=oneOf(x.urgency,['None','Mild','Strong']);b.color=oneOf(x.color,['Brown','Light brown','Yellow','Green','Very dark / black','Red','Pale / clay','Other']);b.pain=number(x.pain,10,false);if(!Number.isInteger(b.pain))throw Error('Discomfort must be a whole number from 0 to 10.');if(x.details!==undefined)b.details=B.bowelDetails(x.details);return b;}
  function validate(x){
    if(!x||x.version!==1||!Array.isArray(x.meals)||!Array.isArray(x.stools)||!Array.isArray(x.noBowel))throw Error('Choose a Daily Rhythm version 1 JSON backup.');
    if(x.meals.length+x.stools.length+x.noBowel.length>30000)throw Error('This backup has too many entries.');
    const v=empty();v.meals=x.meals.map(meal);v.stools=x.stools.map(stool);v.noBowel=[...new Set(x.noBowel.map(date))];
    const ids=new Set();for(const item of [...v.meals,...v.stools]){if(ids.has(item.id))throw Error('Duplicate entry IDs in backup.');ids.add(item.id);}
    v.noBowel=v.noBowel.filter(d=>!v.stools.some(s=>s.day===d));
    if(!x.goals||typeof x.goals!=='object')throw Error('Invalid goals.');
    for(const n of Object.keys(v.goals)){v.goals[n]=number(x.goals[n],30000);if(v.goals[n]===0)v.goals[n]=null;}
    v.ai=AI.normalize(x.ai);v.body=B.normalize(x.body);
    return v;
  }
  function totals(state,day){const meals=state.meals.filter(m=>m.day===day);const result={count:meals.length,missing:{}};for(const n of nutrients){result[n]=meals.reduce((a,m)=>a+(m[n]??0)*m.servings,0);result.missing[n]=meals.filter(m=>m[n]===null).length;}return result;}
  function upsert(state,kind,item){const x=kind==='meals'?meal(item):stool(item);const v=JSON.parse(JSON.stringify(state));v[kind]=v[kind].filter(e=>e.id!==x.id);v[kind].push(x);if(kind==='stools')v.noBowel=v.noBowel.filter(d=>d!==x.day);return validate(v);}
  function csv(state){const heads=['kind','recorded_day','time_utc','name_or_bristol','portion','servings','calories_kcal','protein_g','carbs_g','fat_g','fiber_g','sugar_g','sodium_mg','ease','urgency','color','discomfort_0_10','notes','details_json'];const rows=[heads];for(const m of [...state.meals].sort((a,b)=>a.at.localeCompare(b.at)))rows.push(['meal',m.day,m.at,m.name,m.portion,m.servings,...nutrients.map(n=>m[n]===null?'':Math.round(m[n]*m.servings*100)/100),'','','','',m.notes,JSON.stringify(m.estimate?{aiEstimate:m.estimate}:{})]);for(const s of [...state.stools].sort((a,b)=>a.at.localeCompare(b.at)))rows.push(['bowel',s.day,s.at,s.type,'','','','','','','','','',s.ease,s.urgency,s.color,s.pain,s.notes,JSON.stringify(s.details||{})]);for(const day of state.noBowel){const row=Array(19).fill('');row[0]='no bowel movement';row[1]=day;rows.push(row);}for(const [kind,items] of Object.entries(state.body||{}))for(const x of items){const row=Array(19).fill('');row[0]=kind;row[1]=x.day;row[2]=x.at;row[17]=x.notes;const {notes,...detail}=x;row[18]=JSON.stringify(detail);rows.push(row);}for(const [day,detail] of Object.entries(state.ai?.context||{})){const row=Array(19).fill('');row[0]='daily check-in';row[1]=day;row[18]=JSON.stringify(detail);rows.push(row);}return rows.map(row=>row.map(v=>{let s=String(v??'');if(/^[=+@\-\t\r\n]/.test(s))s="'"+s;return '"'+s.replace(/"/g,'""')+'"';}).join(',')).join('\r\n');}
  root.RhythmCore={nutrients,bristol,empty,dayOf,offsetDay,id,validate,meal,stool,totals,upsert,csv};
  if(typeof module!=='undefined')module.exports=root.RhythmCore;
})(typeof window!=='undefined'?window:globalThis);
