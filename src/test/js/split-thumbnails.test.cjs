const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const source=fs.readFileSync('src/main/resources/static/app.js','utf8');
const split=source.slice(source.indexOf('async function openInboxSplit('),source.indexOf('let appNavigate='));
test('split uses raster thumbnails and an enlarged image, not the blocked PDF iframe',()=>{
 assert.match(split,/className='split-thumbnails'/);assert.match(split,/pages\/\$\{page\}\/preview/);assert.match(split,/IntersectionObserver/);assert.match(split,/renderThumbs\(all\[index\]\)/);assert.doesNotMatch(split,/<iframe|querySelector\('iframe'\)/);
});
test('raster fetches are serialized and cancelled and blob URLs freed on close',()=>{
 assert.match(split,/rendering\.catch\(\(\)=>\{\}\)\.then/);assert.match(split,/abort\.abort\(\)/);assert.match(split,/imageUrls\.forEach\(url=>URL\.revokeObjectURL\(url\)\)/);assert.match(split,/observer\?\.disconnect/);
});
