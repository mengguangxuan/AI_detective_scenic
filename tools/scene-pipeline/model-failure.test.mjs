import {test} from 'node:test';
import assert from 'node:assert/strict';
import {modelFailureDetails,modelFailureMessage} from './model-failure.mjs';
test('only allowlisted diagnostics enter the displayed failure, never arbitrary provider text',()=>{
 const error=modelFailureDetails({code:'IncompleteOutput',providerStatus:200,finishReason:'length',maxTokens:4500,totalTokens:17,partialContent:'private partial code',providerBody:'must not forward'});
 const message=modelFailureMessage(error);
 assert(message.includes('finish_reason=length'));assert(message.includes('max_tokens=4500'));
 assert(!message.includes('private partial code'));assert(!message.includes('must not forward'));
 assert.equal(error.totalTokens,17);assert.equal(error.partialContent,'private partial code');
 assert(!Object.hasOwn(error,'providerBody'));
 assert.equal(modelFailureDetails({code:'invented secret error'}),null);
 assert.equal(modelFailureDetails({code:'__proto__'}),null);
 assert.equal(modelFailureDetails({code:'IncompleteOutput',finishReason:'secret message'}).finishReason,null);
 assert.equal(modelFailureDetails({code:'IncompleteOutput',partialContent:'x'.repeat(100000)}).partialContent.length,90000);
});
