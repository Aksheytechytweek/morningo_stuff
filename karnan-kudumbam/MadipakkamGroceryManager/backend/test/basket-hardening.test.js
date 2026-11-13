import test from 'node:test';
import assert from 'node:assert/strict';
import { rankCompleteBaskets } from '../src/basket.js';
const lines=[{id:'a',name:'Rice',quantity:1},{id:'b',name:'Milk',quantity:1}];
function quote(providerId,items,extra={}) {return {providerId,providerName:providerId,serviceable:true,checkoutQuoteAvailable:true,items,deliveryFee:10,handlingFee:0,platformFee:0,smallCartFee:0,tax:0,discount:0,...extra};}
test('incomplete cheaper basket never beats complete basket',()=>{
 const result=rankCompleteBaskets(lines,[quote('cheap',[{requestLineId:'a',available:true,lineTotal:1}]),quote('complete',[{requestLineId:'a',available:true,lineTotal:30},{requestLineId:'b',available:true,lineTotal:20}])]);
 assert.equal(result.recommendation.providerId,'complete');
 assert.deepEqual(result.candidates[0].missingItems,['Milk']);
});
test('negative prices and fees are not eligible',()=>{
 const items=[{requestLineId:'a',available:true,lineTotal:20},{requestLineId:'b',available:true,lineTotal:20}];
 const result=rankCompleteBaskets(lines,[quote('negative-price',[{...items[0],lineTotal:-1},items[1]]),quote('negative-fee',items,{deliveryFee:-20})]);
 assert.equal(result.recommendation,null);
});
