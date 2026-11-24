import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const source = ts.transpileModule(readFileSync(new URL('../src/sourcePresentation.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
const { sourceScoreKind, sourceScoreLabel, sourceScoreValue } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
for (const [kind, label] of [['RRF', 'Combined search match'], ['BM25', 'Word match'], ['VECTOR', 'Meaning match']]) {
  const document = { score: 2.2, metadata: { retrievalScores: { scoreType: kind } } };
  assert.equal(sourceScoreKind(document), kind);
  assert.equal(sourceScoreLabel(document), label);
  assert.equal(sourceScoreValue(document, 2.2), '2.2000');
  assert.ok(!sourceScoreLabel(document).includes('%'));
}
assert.equal(sourceScoreLabel({ score: .85 }), '85.0% match');
assert.equal(sourceScoreLabel({ score: 0 }), '0.0% match');
assert.equal(sourceScoreLabel({ score: NaN }), null);
assert.equal(sourceScoreKind({ metadata: { retrievalScores: [] } }), null);
console.log('Source-score presentation passed: typed retrieval scores never become confidence percentages.');
