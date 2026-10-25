const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const source=require('./app-source.cjs')();
const split=source.slice(source.indexOf('async function openInboxSplit('),source.indexOf('let appNavigate='));
test('split uses raster thumbnails and an enlarged image, not the blocked PDF iframe',()=>{
 assert.match(split,/className='evidence-page-thumb'/);assert.match(split,/class="evidence-pages"/);assert.match(split,/pages\/\$\{page\}\/preview/);assert.match(split,/IntersectionObserver/);assert.match(split,/renderThumbs\(all\[index\]\)/);assert.match(split,/markRanges\(\)/);assert.doesNotMatch(split,/<iframe|querySelector\('iframe'\)/);
});
test('raster fetches are serialized and cancelled and blob URLs freed on close',()=>{
 assert.match(split,/rendering\.catch\(\(\)=>\{\}\)\.then/);assert.match(split,/abort\.abort\(\)/);assert.match(split,/imageUrls\.forEach\(url=>URL\.revokeObjectURL\(url\)\)/);assert.match(split,/observer\?\.disconnect/);
});
