import assert from 'node:assert/strict'
import test from 'node:test'
import { proposalToContent } from './proposal-content.mjs'
const metadata={recipient:'Fictional customer',date:'6 November 2026'}
test('Markdown is parsed into safe document runs without executing HTML',()=>{
  const value=proposalToContent('# Example proposal\n\nPrepared for review\n\n## Scope\n\nA **clear** [link](https://example.test/).\n\n<!-- pagebreak -->\n\n1. First step\n2. Second step\n',metadata)
  assert.equal(value.title,'Example proposal')
  assert.equal(value.sections.length,1)
  assert.deepEqual(value.sections[0].blocks[0].runs,[{text:'A '},{text:'clear',bold:true},{text:' '},{text:'link',href:'https://example.test/'},{text:'.'}])
  assert.equal(value.sections[0].blocks[1].ordered,true)
  assert.equal(value.sections[0].blocks[1].items.length,2)
})
test('unsupported executable content is rejected before publication',()=>{
  assert.throws(()=>proposalToContent('# Example\n\n## Scope\n\n<script>alert(1)</script>',metadata),/UNSUPPORTED_BLOCK/)
  assert.throws(()=>proposalToContent('# Example\n\n## Scope\n\n[click](javascript:alert%281%29)',metadata),/UNSAFE_LINK/)
  assert.throws(()=>proposalToContent('# Example',metadata),/INCOMPLETE_PROPOSAL/)
})
