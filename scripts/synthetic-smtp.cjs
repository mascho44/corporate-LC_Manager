// Test-only, in-memory SMTP sink. It receives synthetic messages and never relays mail.
const net=require('node:net');
async function startSyntheticSmtp(port=18087){
 const messages=[],sockets=new Set();
 const server=net.createServer(socket=>{sockets.add(socket);socket.on('close',()=>sockets.delete(socket));socket.on('error',()=>{});let buffer='',data=false,lines=[];socket.write('220 synthetic.local ESMTP\r\n');
  socket.on('data',chunk=>{buffer+=chunk.toString('utf8');let at;while((at=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,at).replace(/\r$/,'');buffer=buffer.slice(at+1);
   if(data){if(line==='.') {messages.push(lines.join('\n'));lines=[];data=false;socket.write('250 accepted\r\n');}else lines.push(line.startsWith('..')?line.slice(1):line);continue;}
   if(/^EHLO|^HELO/i.test(line))socket.write('250 synthetic.local\r\n');else if(/^DATA$/i.test(line)){data=true;socket.write('354 finish with dot\r\n');}else if(/^QUIT$/i.test(line)){socket.end('221 bye\r\n');}else if(/^MAIL FROM:|^RCPT TO:|^RSET|^NOOP/i.test(line))socket.write('250 OK\r\n');else socket.write('502 unsupported\r\n');
  }});
 });
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'0.0.0.0',resolve);});
 return {tokenFor(email){const message=[...messages].reverse().find(m=>m.includes(email));if(!message)throw Error('Synthetic invitation email not received.');const decoded=message.replace(/=\n/g,'').replace(/=3D/g,'=');const match=decoded.match(/#token=([A-Za-z0-9_-]{43})/);if(!match)throw Error('Synthetic invitation token missing.');return match[1];},async close(){for(const socket of sockets)socket.destroy();await new Promise(resolve=>server.close(resolve));}};
}
module.exports={startSyntheticSmtp};
