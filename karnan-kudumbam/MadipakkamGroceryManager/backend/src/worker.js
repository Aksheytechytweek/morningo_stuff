import { verifyFirebaseIdToken } from './firebase-auth.js';
import { createExpenseReportPdf, istDateTime, bytesToBase64 } from './reports.js';
import { extractFreeCredits, normaliseSearchResults, buildProviderSummaries, maximumFreeCreditsNeeded } from './rewalto.js';

const BASE_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET,POST,PUT,OPTIONS',
  'access-control-allow-headers': 'authorization,content-type'
};
const json = (body, status=200) => new Response(JSON.stringify(body), {status, headers: BASE_HEADERS});

function fsEncode(value) {
  if (value === null || value === undefined) return {nullValue:null};
  if (typeof value === 'string') return {stringValue:value};
  if (typeof value === 'boolean') return {booleanValue:value};
  if (Number.isInteger(value)) return {integerValue:String(value)};
  if (typeof value === 'number') return {doubleValue:value};
  return {stringValue:String(value)};
}
function fsFields(fields) { return Object.fromEntries(Object.entries(fields).map(([k,v])=>[k,k==='expiresAt'&&typeof v==='string'?{timestampValue:v}:fsEncode(v)])); }
function fsDecode(value) {
  if (!value || typeof value !== 'object') return null;
  if ('stringValue' in value) return value.stringValue;
  if ('integerValue' in value) return Number(value.integerValue);
  if ('doubleValue' in value) return Number(value.doubleValue);
  if ('booleanValue' in value) return value.booleanValue;
  if ('timestampValue' in value) return value.timestampValue;
  if ('nullValue' in value) return null;
  if ('arrayValue' in value) return (value.arrayValue.values ?? []).map(fsDecode);
  if ('mapValue' in value) return Object.fromEntries(Object.entries(value.mapValue.fields ?? {}).map(([k,v])=>[k,fsDecode(v)]));
  return null;
}
async function firestore(env, token, path, init={}) {
  if (!env.FIREBASE_PROJECT_ID) throw new Error('FIREBASE_PROJECT_ID is not configured');
  const url = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/databases/(default)/documents/${path}`;
  const response = await fetch(url,{...init,headers:{authorization:`Bearer ${token}`,'content-type':'application/json',...(init.headers??{})}});
  const raw=await response.text(); let data={}; try{data=raw?JSON.parse(raw):{};}catch{}
  if(!response.ok) throw Object.assign(new Error(data.error?.message??`Firestore HTTP ${response.status}`),{status:response.status});
  return data;
}
async function auth(request, env) {
  const header=request.headers.get('authorization')??'';
  if(!header.startsWith('Bearer ')) throw Object.assign(new Error('Missing Firebase ID token'),{status:401});
  try{return await verifyFirebaseIdToken(header.slice(7),env.FIREBASE_PROJECT_ID);}
  catch{throw Object.assign(new Error('Invalid authentication token'),{status:401});}
}
async function rewalto(env,path,params={}) {
  if(!env.REWALTO_API_KEY) throw new Error('REWALTO_API_KEY is not configured');
  const url=new URL(`https://api.rewalto.com${path}`);
  Object.entries(params).forEach(([k,v])=>url.searchParams.set(k,String(v)));
  const response=await fetch(url,{headers:{authorization:`Bearer ${env.REWALTO_API_KEY}`,accept:'application/json'},signal:AbortSignal.timeout(20000)});
  const raw=await response.text();let data={};try{data=raw?JSON.parse(raw):{};}catch{data={message:raw.slice(0,200)};}
  if(!response.ok)throw Object.assign(new Error(data.error?.message??data.message??`Rewalto HTTP ${response.status}`),{status:response.status,providerCode:data.error?.code??data.code});
  return data;
}
async function rewaltoRoute(request,env,path) {
  if(request.method==='GET'&&path==='/v1/rewalto/sample'){
    try{
      const [v,c]=await Promise.all([fetch('https://api.rewalto.com/v1/version'),fetch('https://api.rewalto.com/v1/catalogue/products?status=live')]);
      if(!v.ok||!c.ok)throw new Error('Public sample unavailable');
      const version=await v.json(),catalogue=await c.json();
      const rows=Array.isArray(catalogue)?catalogue:(catalogue.products??catalogue.datasets??catalogue.items??catalogue.data??[]);
      return json({provider:'Rewalto',sampleFree:true,creditsConsumed:0,version:version.version??version.release??version,liveCatalogueAvailable:Array.isArray(rows),liveDatasetCount:Array.isArray(rows)?rows.length:null,note:'Public catalogue test only; no price-search credits consumed.'});
    }catch(e){return json({error:'Rewalto public sample unavailable',detail:e.message},503);}
  }
  if(request.method==='GET'&&path==='/v1/rewalto/balance'){
    try{const b=await rewalto(env,'/v1/credits/balance');const free=extractFreeCredits(b);if(free===null)return json({error:'Free-credit balance cannot be verified; price checks blocked.'},502);return json({provider:'Rewalto',freeCredits:free,paidUsageAllowed:false,policy:'free-credits-only'});}
    catch(e){return json({error:'Rewalto balance unavailable; no search started.',providerCode:e.providerCode},e.status??503);}
  }
  if(request.method==='POST'&&path==='/v1/rewalto/basket/compare'){
    try{
      const {pincode,lines}=await request.json();
      if(!/^[1-9][0-9]{5}$/.test(String(pincode??''))||!Array.isArray(lines)||lines.length<1||lines.length>20)return json({error:'Provide a six-digit PIN and 1–20 basket items.'},400);
      const clean=lines.map((x,i)=>({id:String(x.id??i+1).slice(0,80),name:String(x.name??'').trim().slice(0,120),quantity:String(x.quantity??'').trim().slice(0,60),quantityCount:Math.max(1,Math.min(50,Number(x.quantityCount??1)))}));
      if(clean.some(x=>!x.name||!x.quantity))return json({error:'Every item needs a product name and pack size.'},400);
      const before=extractFreeCredits(await rewalto(env,'/v1/credits/balance')),maximum=maximumFreeCreditsNeeded(clean.length);
      if(before===null)return json({error:'Free-credit balance cannot be verified. Paid usage is blocked.'},503);
      if(before<maximum)return json({error:'Insufficient free credits. No searches were made.',freeCredits:before,maximumCredits:maximum,paidUsageAllowed:false},402);
      const observations=[];
      for(const line of clean){
        const current=extractFreeCredits(await rewalto(env,'/v1/credits/balance'));
        if(current===null||current<1)break;
        const result=await rewalto(env,'/v1/data/listings/search',{q:`${line.name} ${line.quantity}`,pincode,limit:10,include:'latest'});
        observations.push(...normaliseSearchResults(result,{...line,pincode}));
      }
      const providers=buildProviderSummaries(clean,observations);
      const after=extractFreeCredits(await rewalto(env,'/v1/credits/balance'));
      const recommendation=providers.filter(x=>x.complete).sort((a,b)=>(a.productSubtotal??Infinity)-(b.productSubtotal??Infinity))[0]??null;
      return json({provider:'Rewalto',pincode,fetchedAt:new Date().toISOString(),sourceNote:'Stored public-page observations, not guaranteed checkout quotes. Verify timestamp, stock and price in retailer app.',freeCreditsBefore:before,freeCreditsAfter:after,maximumCreditsReservedByPolicy:maximum,paidUsageAllowed:false,partialCheck:clean.some(x=>!observations.some(o=>o.requestLineId===x.id)),recommendation,providers,observations});
    }catch(e){return json({error:'Price data unavailable. No paid fallback will be attempted.',providerCode:e.providerCode},e.status===402?402:e.status===429?429:503);}
  }
  return json({error:'Not found'},404);
}
async function getDocument(env,token,path){
  try{return await firestore(env,token,path);}catch(e){if(e.status===404)return null;throw e;}
}
function decodeFields(fields={}){return Object.fromEntries(Object.entries(fields).map(([k,v])=>[k,fsDecode(v)]));}
async function isHouseholdMember(env,token,householdId,uid){
  if(householdId===uid)return true; // Backward-compatible owner household.
  const member=await getDocument(env,token,`households/${encodeURIComponent(householdId)}/members/${encodeURIComponent(uid)}`);
  return Boolean(member);
}
async function householdRoute(request,env,user){
  const token=request.headers.get('authorization').slice(7);
  if(request.method==='POST'&&new URL(request.url).pathname==='/v1/households'){
    const householdId=user.sub;
    const existing=await getDocument(env,token,`households/${encodeURIComponent(householdId)}`);
    if(!existing)await firestore(env,token,`households/${encodeURIComponent(householdId)}`,{method:'PATCH',body:JSON.stringify({fields:fsFields({ownerUid:user.sub,name:String(user.name??user.email??'Family').slice(0,80),createdAt:new Date().toISOString()})})});
    const member=await getDocument(env,token,`households/${encodeURIComponent(householdId)}/members/${encodeURIComponent(user.sub)}`);
    if(!member)await firestore(env,token,`households/${encodeURIComponent(householdId)}/members/${encodeURIComponent(user.sub)}`,{method:'PATCH',body:JSON.stringify({fields:fsFields({uid:user.sub,email:String(user.email??''),role:'owner',joinedAt:new Date().toISOString()})})});
    return json({created:true,householdId,role:'owner'});
  }
  if(request.method==='POST'&&new URL(request.url).pathname==='/v1/households/invitations'){
    const b=await request.json(),householdId=String(b.householdId??user.sub),email=String(b.email??'').trim();
    if(!/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email))return json({error:'Enter a valid invite email.'},400);
    if(!await isHouseholdMember(env,token,householdId,user.sub))return json({error:'Household membership required.'},403);
    const owner=householdId===user.sub||decodeFields((await getDocument(env,token,`households/${encodeURIComponent(householdId)}/members/${encodeURIComponent(user.sub)}`))?.fields).role==='owner';
    if(!owner)return json({error:'Only the household owner can invite members.'},403);
    const code=crypto.randomUUID().replaceAll('-','');
    const expiresAt=new Date(Date.now()+7*86400000).toISOString();
    await firestore(env,token,`households/${encodeURIComponent(householdId)}/invitations/${code}`,{method:'PATCH',body:JSON.stringify({fields:fsFields({email,role:'member',status:'pending',createdBy:user.sub,createdAt:new Date().toISOString(),expiresAt})})});
    return json({created:true,householdId,inviteCode:code,invitedEmail:email,expiresAt,delivery:'share-code-manually'});
  }
  if(request.method==='POST'&&new URL(request.url).pathname==='/v1/households/join'){
    const b=await request.json(),householdId=String(b.householdId??''),code=String(b.inviteCode??'').trim();
    if(!/^[A-Za-z0-9]{24,64}$/.test(code)||!householdId)return json({error:'Household ID and valid invitation code are required.'},400);
    const invite=await getDocument(env,token,`households/${encodeURIComponent(householdId)}/invitations/${encodeURIComponent(code)}`);
    const fields=decodeFields(invite?.fields);
    if(!invite||fields.status!=='pending'||String(fields.email??'').toLowerCase()!==String(user.email??'').toLowerCase()||Date.parse(fields.expiresAt??'')<=Date.now())return json({error:'Invitation is invalid, expired, or addressed to another email.'},403);
    await firestore(env,token,`households/${encodeURIComponent(householdId)}/members/${encodeURIComponent(user.sub)}`,{method:'PATCH',body:JSON.stringify({fields:fsFields({uid:user.sub,email:String(user.email??''),role:'member',joinedAt:new Date().toISOString(),inviteCode:code})})});
    await firestore(env,token,`households/${encodeURIComponent(householdId)}/invitations/${encodeURIComponent(code)}`,{method:'PATCH',body:JSON.stringify({fields:fsFields({...fields,status:'accepted',acceptedBy:user.sub,acceptedAt:new Date().toISOString()})})});
    return json({joined:true,householdId,role:'member'});
  }
  return json({error:'Not found'},404);
}
async function expenseRoute(request,env,user){
  const token=request.headers.get('authorization').slice(7);
  if(request.method==='POST'){
    const {householdId=user.sub,expenses}=await request.json();
    if(!await isHouseholdMember(env,token,String(householdId),user.sub))return json({error:'Household membership required.'},403);
    if(!Array.isArray(expenses)||expenses.length>500)return json({error:'Invalid sync payload'},400);
    const writes=[];
    for(const x of expenses){
      if(!x?.id||!/^[A-Za-z0-9_-]{1,160}$/.test(String(x.id))||!/^\d{4}-\d{2}-\d{2}$/.test(String(x.date))||!Number.isFinite(Number(x.amount))||Number(x.amount)<0||Number(x.amount)>10000000)continue;
      writes.push({update:{name:`projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/households/${encodeURIComponent(String(householdId))}/expenses/${encodeURIComponent(String(x.id))}`,fields:fsFields({date:String(x.date),amount:Number(x.amount),category:String(x.category??'Other').slice(0,80),note:String(x.note??'').slice(0,500),payment:String(x.payment??'Cash').slice(0,40)})}});
    }
    if(writes.length)await firestore(env,token,':commit',{method:'POST',body:JSON.stringify({writes})});
    return json({synced:true,count:writes.length});
  }
  if(request.method==='GET'){
    const householdId=new URL(request.url).searchParams.get('householdId')??user.sub;
    if(!await isHouseholdMember(env,token,householdId,user.sub))return json({error:'Household membership required.'},403);
    const data=await firestore(env,token,`households/${encodeURIComponent(householdId)}/expenses?pageSize=1000`);
    return json({householdId,expenses:(data.documents??[]).map(d=>({id:d.name.split('/').pop(),...Object.fromEntries(Object.entries(d.fields??{}).map(([k,v])=>[k,fsDecode(v)]))}))});
  }
  return json({error:'Method not allowed'},405);
}
async function reportRoute(request,env,user){
  if(request.method!=='PUT')return json({error:'Method not allowed'},405);
  const b=await request.json(),email=String(b.email??'').trim().toLowerCase(),verified=String(user.email??'').trim().toLowerCase(),time=String(b.reportTime??'20:00'),budget=Number(b.monthlyBudget??15000),householdId=String(b.householdId??user.sub);
  if(user.email_verified!==true||!verified||email!==verified)return json({error:'Use the verified email address on the signed-in account.'},403);
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)||!Number.isFinite(budget)||budget<=0)return json({error:'Invalid report email, time or monthly budget.'},400);
  const token=request.headers.get('authorization').slice(7);
  if(!await isHouseholdMember(env,token,householdId,user.sub))return json({error:'Household membership required.'},403);
  const verifiedEmail=String(user.email??'').trim();
  await firestore(env,token,`reportSubscriptions/${encodeURIComponent(user.sub)}`,{method:'PATCH',body:JSON.stringify({fields:fsFields({email:verifiedEmail,enabled:Boolean(b.enabled??true),monthlyBudget:budget,reportTime:time,timeZone:'Asia/Kolkata',householdId})})});
  return json({saved:true,email:verifiedEmail,enabled:Boolean(b.enabled??true),reportTime:time,timeZone:'Asia/Kolkata',deliveryStatus:env.FIREBASE_SERVICE_ACCOUNT_JSON&&env.RESEND_API_KEY&&env.REPORT_FROM_EMAIL?'scheduled':'pending-provider-setup'});
}

function base64Url(value){
  const bytes=typeof value==='string'?new TextEncoder().encode(value):value;
  let binary='';for(let i=0;i<bytes.length;i++)binary+=String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_');
}
async function firebaseAdminToken(env){
  if(!env.FIREBASE_SERVICE_ACCOUNT_JSON)throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON is not configured');
  const account=JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  if(!account.client_email||!account.private_key)throw new Error('Firebase service account secret is invalid');
  const now=Math.floor(Date.now()/1000);
  const header=base64Url(JSON.stringify({alg:'RS256',typ:'JWT'}));
  const claim=base64Url(JSON.stringify({iss:account.client_email,scope:'https://www.googleapis.com/auth/datastore',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600}));
  const unsigned=header+'.'+claim;
  const pem=account.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');
  const der=Uint8Array.from(atob(pem),ch=>ch.charCodeAt(0));
  const key=await crypto.subtle.importKey('pkcs8',der,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
  const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(unsigned));
  const assertion=unsigned+'.'+base64Url(new Uint8Array(signature));
  const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion})});
  const data=await response.json();if(!response.ok||!data.access_token)throw new Error('Firebase service-account token exchange failed');
  return data.access_token;
}
async function adminFirestore(env,token,path,init={}){
  const url=`https://firestore.googleapis.com/v1/projects/${encodeURIComponent(env.FIREBASE_PROJECT_ID)}/databases/(default)/documents/${path}`;
  const response=await fetch(url,{...init,headers:{authorization:`Bearer ${token}`,'content-type':'application/json',...(init.headers??{})}});
  const raw=await response.text();let data={};try{data=raw?JSON.parse(raw):{};}catch{}
  if(!response.ok)throw Object.assign(new Error(data.error?.message??`Firestore admin HTTP ${response.status}`),{status:response.status});
  return data;
}
async function markDelivery(env,token,id,fields,create=false){
  const path='reportDeliveries/'+encodeURIComponent(id);
  return adminFirestore(env,token,create?'reportDeliveries?documentId='+encodeURIComponent(id):path,{method:create?'POST':'PATCH',body:JSON.stringify({fields:fsFields(fields)})});
}
async function sendDailyReports(env,at=new Date()){
  if(!env.FIREBASE_PROJECT_ID||!env.FIREBASE_SERVICE_ACCOUNT_JSON||!env.RESEND_API_KEY||!env.REPORT_FROM_EMAIL)throw new Error('Daily report requires Firebase service account, Resend API key and verified sender configuration');
  const {date,time}=istDateTime(at),token=await firebaseAdminToken(env);
  const subscriptions=await adminFirestore(env,token,'reportSubscriptions?pageSize=1000');
  const sent=[],skipped=[],failed=[];
  for(const doc of subscriptions.documents??[]){
    const sub=decodeFields(doc.fields),uid=doc.name.split('/').pop();
    if(!sub.enabled||sub.reportTime!==time||!sub.email)continue;
    const householdId=String(sub.householdId??uid);
    const deliveryId='daily_'+uid+'_'+date.replace(/-/g,'')+'_'+time.replace(':','');
    let prior=null;try{prior=await adminFirestore(env,token,'reportDeliveries/'+encodeURIComponent(deliveryId));}catch(e){if(e.status!==404)throw e;}
    if(decodeFields(prior?.fields).status==='sent'){skipped.push(uid);continue;}
    try{
      const expenses=await adminFirestore(env,token,`households/${encodeURIComponent(householdId)}/expenses?pageSize=1000`);
      const rows=(expenses.documents??[]).map(item=>({id:item.name.split('/').pop(),...decodeFields(item.fields)}));
      const pdf=createExpenseReportPdf({date,email:sub.email,budget:sub.monthlyBudget??15000,expenses:rows});
      const attachment=bytesToBase64(pdf);
      await markDelivery(env,token,deliveryId,{uid,householdId,email:sub.email,date,reportTime:time,status:'sending',updatedAt:new Date().toISOString()},!prior);
      const response=await fetch('https://api.resend.com/emails',{method:'POST',headers:{authorization:`Bearer ${env.RESEND_API_KEY}`,'content-type':'application/json'},body:JSON.stringify({from:env.REPORT_FROM_EMAIL,to:[sub.email],subject:`Karnan Kudumbam daily report - ${date}`,html:`<p>Your household expense report for ${date} is attached.</p><p>This report contains recorded expenses only; retailer prices are observations, not checkout quotes.</p>`,attachments:[{filename:`karnan-kudumbam-${date}.pdf`,content:attachment}]})});
      const result=await response.json();if(!response.ok)throw new Error(result.message??'Email provider rejected the report');
      await markDelivery(env,token,deliveryId,{uid,householdId,email:sub.email,date,reportTime:time,status:'sent',providerId:result.id??'',sentAt:new Date().toISOString()});
      sent.push(uid);
    }catch(e){
      try{await markDelivery(env,token,deliveryId,{uid,householdId,email:sub.email,date,reportTime:time,status:'failed',error:String(e.message??e).slice(0,300),updatedAt:new Date().toISOString()},!prior);}catch{}
      failed.push({uid,error:String(e.message??e).slice(0,180)});
    }
  }
  return {date,time,sent:sent.length,skipped:skipped.length,failed};
}
async function internalReportRoute(request,env){
  if(!env.DAILY_JOB_SECRET||request.headers.get('x-job-secret')!==env.DAILY_JOB_SECRET)return json({error:'Unauthorized'},401);
  if(request.method!=='POST')return json({error:'Method not allowed'},405);
  try{return json({ok:true,...await sendDailyReports(env)});}
  catch(e){return json({error:e.message??'Daily report job failed'},503);}
}
export default {
  async scheduled(controller,env,ctx){if(!env.FIREBASE_SERVICE_ACCOUNT_JSON||!env.RESEND_API_KEY||!env.REPORT_FROM_EMAIL)return;ctx.waitUntil(sendDailyReports(env,new Date(controller.scheduledTime??Date.now())));},
  async fetch(request,env){
    const url=new URL(request.url);
    if(request.method==='OPTIONS')return new Response(null,{status:204,headers:BASE_HEADERS});
    if(url.pathname==='/health'&&request.method==='GET')return json({ok:true,service:'karnan-kudumbam-api',runtime:'cloudflare-workers'});
    try{
      if(url.pathname==='/internal/jobs/daily-report')return await internalReportRoute(request,env);
      const user=await auth(request,env);
      if(url.pathname.startsWith('/v1/rewalto/'))return await rewaltoRoute(request,env,url.pathname);
      if(url.pathname.startsWith('/v1/households'))return await householdRoute(request,env,user);
            if(url.pathname==='/v1/expenses/sync')return await expenseRoute(request,env,user);
      if(url.pathname==='/v1/report-subscription')return await reportRoute(request,env,user);

      return json({error:'Not found'},404);
    }catch(e){return json({error:e.message??'Request failed'},e.status??500);}
  }
};
