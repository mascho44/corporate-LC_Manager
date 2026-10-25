const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),{spawnSync}=require('node:child_process');
const script=path.resolve(__dirname,'../../../scripts/audit-anchor.sh');
const H1='a'.repeat(64),H2='b'.repeat(64),H3='c'.repeat(64);
function setup(){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'anchor-'));const rows=path.join(dir,'rows.txt'),file=path.join(dir,'anchors.log');
 const env={...process.env,AUDIT_ANCHOR_FILE:file,AUDIT_ANCHOR_SOURCE_CMD:`cat '${rows}'`,RESTIC_REPOSITORY:'',RESTIC_PASSWORD_FILE:''};
 const run=(...args)=>spawnSync('bash',[script,...args],{env,encoding:'utf8'});
 const set=lines=>fs.writeFileSync(rows,lines.join('\n')+'\n');
 return {dir,file,run,set};
}
test('first run writes a chained anchor line per tenant and the log verifies',()=>{
 const s=setup();s.set([`default|t|10|10|${H1}|0|`,`firma|t|3|3|${H2}|0|`]);
 const r=s.run();assert.equal(r.status,0,r.stderr);
 const lines=fs.readFileSync(s.file,'utf8').trim().split('\n');assert.equal(lines.length,2);
 assert.match(lines[0],/\|default\|OK\|10\|10\|a{64}\|0\|0{64}$/);assert.doesNotMatch(lines[1],/\|0{64}$/);
 assert.equal(s.run('--check-log').status,0);
});
test('growing chains are accepted, the head is appended each run',()=>{
 const s=setup();s.set([`default|t|10|10|${H1}|0|`]);assert.equal(s.run().status,0);
 s.set([`default|t|12|12|${H2}|0|`]);const r=s.run();assert.equal(r.status,0,r.stderr);
 assert.equal(fs.readFileSync(s.file,'utf8').trim().split('\n').length,2);assert.equal(s.run('--check-log').status,0);
});
test('same sequence with a different hash and a smaller sequence raise an alarm',()=>{
 const s=setup();s.set([`default|t|10|10|${H1}|0|`]);s.run();
 s.set([`default|t|10|10|${H2}|0|`]);let r=s.run();assert.equal(r.status,1);assert.match(r.stderr,/weicht vom gesicherten Stand ab/);
 s.set([`default|t|8|8|${H3}|0|`]);r=s.run();assert.equal(r.status,1);assert.match(r.stderr,/zurueckgegangen/);
});
test('a broken chain in the database is reported and recorded as BAD',()=>{
 const s=setup();s.set([`default|f|10|10|${H1}|0|Hash stimmt nicht`]);
 const r=s.run();assert.equal(r.status,1);assert.match(r.stderr,/defekt.*Hash stimmt nicht/);
 assert.match(fs.readFileSync(s.file,'utf8'),/\|default\|BAD\|/);
});
test('tampering with the anchor file is detected before anything is added',()=>{
 const s=setup();s.set([`default|t|10|10|${H1}|0|`]);s.run();s.set([`default|t|11|11|${H2}|0|`]);s.run();
 const lines=fs.readFileSync(s.file,'utf8').trim().split('\n');lines[0]=lines[0].replace(H1,H3);fs.writeFileSync(s.file,lines.join('\n')+'\n');
 assert.equal(s.run('--check-log').status,1);
 const before=fs.readFileSync(s.file,'utf8');s.set([`default|t|12|12|${H2}|0|`]);const r=s.run();
 assert.equal(r.status,1);assert.match(r.stderr,/veraendert|gekuerzt/);assert.equal(fs.readFileSync(s.file,'utf8'),before);
});
test('removing the first line is detected',()=>{
 const s=setup();s.set([`default|t|10|10|${H1}|0|`]);s.run();s.set([`default|t|11|11|${H2}|0|`]);s.run();
 const lines=fs.readFileSync(s.file,'utf8').trim().split('\n');fs.writeFileSync(s.file,lines.slice(1).join('\n')+'\n');
 assert.equal(s.run('--check-log').status,1);
});
test('an unreadable source is a technical error and leaves the file unchanged',()=>{
 const s=setup();s.set([`default|t|10|10|${H1}|0|`]);s.run();const before=fs.readFileSync(s.file,'utf8');
 fs.unlinkSync(path.join(s.dir,'rows.txt'));const r=s.run();assert.equal(r.status,2);assert.equal(fs.readFileSync(s.file,'utf8'),before);
});
test('the anchor file is private',()=>{
 const s=setup();s.set([`default|t|1|1|${H1}|0|`]);s.run();assert.equal(fs.statSync(s.file).mode&0o077,0);
});
