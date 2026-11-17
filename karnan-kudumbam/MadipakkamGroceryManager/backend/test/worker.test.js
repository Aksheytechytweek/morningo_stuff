import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/worker.js';
test('health endpoint is public and identifies the Worker runtime',async()=>{
 const response=await worker.fetch(new Request('https://api.example.test/health'),{});
 assert.equal(response.status,200);const body=await response.json();assert.equal(body.ok,true);assert.equal(body.runtime,'cloudflare-workers');
});
test('all protected household, expense, report and Rewalto endpoints reject missing Firebase ID tokens',async()=>{
 const paths=[
  '/v1/expenses/sync',
  '/v1/households',
  '/v1/households/invitations',
  '/v1/households/join',
  '/v1/report-subscription',
  '/v1/rewalto/sample',
  '/v1/rewalto/balance',
  '/v1/rewalto/basket/compare'
 ];
 for(const path of paths){
  const response=await worker.fetch(new Request('https://api.example.test'+path),{});
  assert.equal(response.status,401,path);
  assert.match((await response.json()).error,/Missing Firebase ID token/,path);
 }
});
test('OPTIONS preflight is handled without authentication',async()=>{
 const response=await worker.fetch(new Request('https://api.example.test/v1/expenses/sync',{method:'OPTIONS'}),{});
 assert.equal(response.status,204);assert.equal(response.headers.get('access-control-allow-origin'),'*');
});

test('manual daily report job is protected by a dedicated secret',async()=>{
 const request=new Request('https://api.example.test/internal/jobs/daily-report',{method:'POST'});
 const denied=await worker.fetch(request,{});
 assert.equal(denied.status,401);
 const wrong=await worker.fetch(new Request('https://api.example.test/internal/jobs/daily-report',{method:'POST',headers:{'x-job-secret':'wrong'}}),{DAILY_JOB_SECRET:'expected'});
 assert.equal(wrong.status,401);
});
