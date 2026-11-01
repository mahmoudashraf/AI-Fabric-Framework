import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/sourcePresentation.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } });
const { structuredSourceContent, sourceContentPreview } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`);

test('ordinary policy text and malformed JSON remain unchanged', () => {
  for (const content of ['Bring photo identification.', '{incomplete', '"plain JSON string"', 'null', '42']) {
    assert.equal(sourceContentPreview(content), content);
    assert.equal(structuredSourceContent(content), null);
  }
});
test('object/array source previews use structure, never infer domain or disclose raw field values', () => {
  assert.equal(sourceContentPreview('{"results":[{"name":"Example"}],"success":true}'), 'Structured evidence: 2 fields. Open to inspect.');
  assert.equal(sourceContentPreview('[{"id":"first"}]'), 'Structured evidence: 1 record. Open to inspect.');
  assert.equal(sourceContentPreview('[]'), 'Structured evidence: 0 records. Open to inspect.');
});
test('source parsing preserves evidence fields rather than rewriting business facts', () => {
  const original = '{"status":"UNKNOWN","data":{"price":null,"count":0,"enabled":false},"notes":"<script>not executable</script>"}';
  assert.deepEqual(structuredSourceContent(original), JSON.parse(original));
  assert.equal(JSON.stringify(structuredSourceContent(original)), original);
});
