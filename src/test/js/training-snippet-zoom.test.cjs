const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
test('snippet blob URL stays valid so the zoom dialog can reuse it',()=>{
 const start=source.indexOf('async function loadTrainingSnippets'),end=source.indexOf('function openSnippetZoom');
 assert.ok(start>=0&&end>start);
 const body=source.slice(start,end),onload=body.slice(body.indexOf('img.onload'),body.indexOf('img.onerror'));
 assert.equal(onload.includes('revokeObjectURL'),false);
});
