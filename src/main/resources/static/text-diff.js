// Bounded word-level LCS: large texts fall back to a changed-span comparison.
function diffEscape(value){return String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function textDiff(before,after){
    const a=String(before??'').match(/\s+|[^\s]+/g)||[],b=String(after??'').match(/\s+|[^\s]+/g)||[];
    const render=(tokens,kind)=>tokens.length?`<${kind}>${diffEscape(tokens.join(''))}</${kind}>`:'';
    if(a.length*b.length>250000){let start=0,end=0;while(start<a.length&&start<b.length&&a[start]===b[start])start++;while(end<a.length-start&&end<b.length-start&&a[a.length-1-end]===b[b.length-1-end])end++;const prefix=diffEscape(a.slice(0,start).join('')),suffix=diffEscape(end?a.slice(-end).join(''):'');return {before:prefix+render(a.slice(start,a.length-end),'del')+suffix,after:prefix+render(b.slice(start,b.length-end),'ins')+suffix};}
    const matrix=Array.from({length:a.length+1},()=>new Uint32Array(b.length+1));
    for(let i=a.length-1;i>=0;i--)for(let j=b.length-1;j>=0;j--)matrix[i][j]=a[i]===b[j]?1+matrix[i+1][j+1]:Math.max(matrix[i+1][j],matrix[i][j+1]);
    let i=0,j=0,left='',right='';while(i<a.length||j<b.length){if(i<a.length&&j<b.length&&a[i]===b[j]){const token=diffEscape(a[i++]);j++;left+=token;right+=token;}else if(i<a.length&&(j===b.length||matrix[i+1][j]>=matrix[i][j+1]))left+=render([a[i++]],'del');else right+=render([b[j++]],'ins');}
    return {before:left,after:right};
}
function diffCells(before,after){const result=textDiff(before,after);return `<td class="diff-text">${result.before||'–'}</td><td class="diff-text">${result.after||'–'}</td>`;}
