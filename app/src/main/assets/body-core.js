(function(root){
'use strict';
function n(v,min,max,integer=false){if(v===null)return null;if(typeof v!=='number'||!Number.isFinite(v)||v<min||v>max||(integer&&!Number.isInteger(v)))throw Error('Check the number and allowed range.');return v;}
function text(v,max=2000){if(typeof v!=='string'||v.length>max)throw Error('Text is missing or too long.');return v;}
function choice(v,values){if(!values.includes(v))throw Error('Choose a supported option.');return v;}
function base(v){if(!v||typeof v!=='object'||!/^[-\w]{1,100}$/.test(v.id))throw Error('Invalid event ID.');if(typeof v.at!=='string'||!Number.isFinite(Date.parse(v.at)))throw Error('Invalid event time.');if(!/^\d{4}-\d{2}-\d{2}$/.test(v.day)||!Number.isFinite(Date.parse(v.day+'T12:00Z'))||new Date(v.day+'T12:00Z').toISOString().slice(0,10)!==v.day)throw Error('Invalid event day.');const o={id:v.id,at:new Date(v.at).toISOString(),day:v.day,notes:text(v.notes||'')};if(v.offsetMinutes!==undefined)o.offsetMinutes=n(v.offsetMinutes,-840,840,true);return o;}
function event(kind,v){const o=base(v);if(kind==='vapes'){
 o.hits=n(v.hits,1,500,true);if(o.hits===null)throw Error('Enter the number of vape hits.');
 o.puffSeconds=n(v.puffSeconds,0,60);o.sessionMinutes=n(v.sessionMinutes,0,1440);o.product=text(v.product||'',150);o.thcPercent=n(v.thcPercent,0,100);o.cbdPercent=n(v.cbdPercent,0,100);
 if((o.thcPercent??0)+(o.cbdPercent??0)>100)throw Error('THC and CBD percentages cannot total more than 100.');
 o.purpose=choice(v.purpose,['Unspecified','Recreation','Sleep','Symptom relief','Other']);o.effect=n(v.effect,0,10);
 }else if(kind==='water'){
 o.ml=n(v.ml,1,10000);if(o.ml===null)throw Error('Enter the amount of water.');o.displayUnit=choice(v.displayUnit,['ml','US fl oz']);
 }else if(kind==='caffeine'){
 o.mg=n(v.mg,0,3000);o.source=text(v.source||'',150);o.amount=text(v.amount||'',100);o.basis=choice(v.basis,['Unknown','Label','Estimate']);if(o.mg===null&&!o.source.trim())throw Error('Enter caffeine mg or a source.');if(o.mg===null)o.basis='Unknown';
 }else if(kind==='orgasms'){
 o.count=n(v.count,1,100,true);if(o.count===null)throw Error('Enter an orgasm count.');o.context=choice(v.context,['Unspecified','Solo','Partnered']);o.durationMinutes=n(v.durationMinutes,0,1440);o.pleasure=n(v.pleasure,0,10);o.pain=n(v.pain,0,10);
 }else if(kind==='moods'){
 for(const k of ['mood','energy','anxiety','focus','stress'])o[k]=n(v[k],0,10);
 if(['mood','energy','anxiety','focus','stress'].every(k=>o[k]===null))throw Error('Enter at least one mood or brain rating.');
 o.context=choice(v.context,['Unspecified','Waking','Before meal','After meal','Before vape','After vape','Before orgasm','After orgasm','Bedtime','Other']);
 }else throw Error('Unknown body event.');return o;}
function normalize(v){const out={vapes:[],orgasms:[],moods:[],caffeine:[],water:[]};if(v===undefined)return out;if(!v||typeof v!=='object')throw Error('Invalid body log.');const ids=new Set();for(const k of Object.keys(out)){if(['caffeine','water'].includes(k)&&v[k]===undefined)continue;if(!Array.isArray(v[k])||v[k].length>30000)throw Error('Invalid body event list.');out[k]=v[k].map(x=>event(k,x));for(const x of out[k]){if(ids.has(x.id))throw Error('Duplicate body event ID.');ids.add(x.id);}}return out;}
function bowelDetails(v){if(v===undefined)return undefined;return {minutes:n(v.minutes,0,240),size:choice(v.size,['Unspecified','Small','Medium','Large']),evacuation:choice(v.evacuation,['Unspecified','Complete','Incomplete']),bloating:n(v.bloating,0,10),blood:choice(v.blood,['Unspecified','No','Yes']),mucus:choice(v.mucus,['Unspecified','No','Yes'])};}
const api={event,normalize,bowelDetails};root.RhythmBody=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
