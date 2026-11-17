const KEYS_URL='https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
let keysCache=null,keysExpiry=0;
function decode64(value){const s=value.replace(/-/g,'+').replace(/_/g,'/')+'='.repeat((4-value.length%4)%4);return Uint8Array.from(atob(s),c=>c.charCodeAt(0));}
function decodeJson(value){return JSON.parse(new TextDecoder().decode(decode64(value)));}
export function validateFirebaseClaims(c,projectId,now=Math.floor(Date.now()/1000)){
  if(!c||c.aud!==projectId)throw new Error('Invalid Firebase token audience');
  if(c.iss!==`https://securetoken.google.com/${projectId}`)throw new Error('Invalid Firebase token issuer');
  if(!Number.isFinite(c.exp)||c.exp<=now)throw new Error('Firebase token expired');
  if(!Number.isFinite(c.iat)||c.iat>now+60)throw new Error('Invalid Firebase token issued-at time');
  if(!Number.isFinite(c.auth_time)||c.auth_time>now+60)throw new Error('Invalid Firebase authentication time');
  if(typeof c.sub!=='string'||!c.sub.length||c.sub.length>128||c.sub.includes('/'))throw new Error('Invalid Firebase user ID');
  return c;
}
async function getKeys(fetchImpl=fetch){
  if(keysCache&&Date.now()<keysExpiry)return keysCache;
  const r=await fetchImpl(KEYS_URL,{headers:{accept:'application/json'}});
  if(!r.ok)throw new Error('Firebase verification keys unavailable');
  const body=await r.json();if(!Array.isArray(body.keys)||!body.keys.length)throw new Error('Firebase verification keys unavailable');
  const age=Number((r.headers.get('cache-control')??'').match(/max-age=(\d+)/)?.[1]??1800);
  keysCache=body.keys;keysExpiry=Date.now()+Math.max(60,Math.min(age,3600))*1000;return keysCache;
}
export async function verifyFirebaseIdToken(token,projectId,options={}){
  if(!projectId)throw new Error('FIREBASE_PROJECT_ID is not configured');
  const parts=String(token??'').split('.');if(parts.length!==3)throw new Error('Malformed Firebase ID token');
  const header=decodeJson(parts[0]),claims=validateFirebaseClaims(decodeJson(parts[1]),projectId,options.nowSeconds);
  if(header.alg!=='RS256'||typeof header.kid!=='string')throw new Error('Unsupported Firebase token signature');
  const keys=await getKeys(options.fetchImpl??fetch),jwk=keys.find(k=>k.kid===header.kid&&k.kty==='RSA');
  if(!jwk)throw new Error('Firebase signing key not found');
  const key=await crypto.subtle.importKey('jwk',jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
  const valid=await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,decode64(parts[2]),new TextEncoder().encode(parts[0]+'.'+parts[1]));
  if(!valid)throw new Error('Invalid Firebase token signature');
  return claims;
}
