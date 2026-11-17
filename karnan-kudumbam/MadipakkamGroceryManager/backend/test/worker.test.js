import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/worker.js';
test('health endpoint is public and identifies the Worker runtime',async()=>{
 const response=await worker.fetch(new Request('https://api.example.test/health'),{});
 assert.equal(response.status,200);const body=await response.json();assert.equal(body.ok,true);assert.equal(body.runtime,'cloudflare-workers');
});
test('protected endpoints reject requests without Firebase ID tokens',async()=>{
 const response=await worker.fetch(new Request('https://api.example.test/v1/expenses/sync'),{});
 assert.equal(response.status,401);assert.match((await response.json()).error,/Missing Firebase ID token/);
});
test('OPTIONS preflight is handled without authentication',async()=>{
 const response=await worker.fetch(new Request('https://api.example.test/v1/expenses/sync',{method:'OPTIONS'}),{});
 assert.equal(response.status,204);assert.equal(response.headers.get('access-control-allow-origin'),'*');
});
