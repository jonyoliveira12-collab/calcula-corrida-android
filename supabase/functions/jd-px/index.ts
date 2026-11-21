// Voice tokens and administrative credentials never leave this function.
const headers={"Content-Type":"application/json","Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff","Vary":"Authorization"};
const json=(status:number,value:unknown)=>new Response(JSON.stringify(value),{status,headers});
const enc=new TextEncoder();
const b64=(bytes:Uint8Array)=>btoa(String.fromCharCode(...bytes)).replace(/=/g,"").replace(/\+/g,"-").replace(/\//g,"_");
const uuid=(v:unknown)=>typeof v==='string'&&/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(v);
const url=Deno.env.get('SUPABASE_URL')!,serverKey=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
async function rpc(action:string,user:string|null,session:string|null,data:unknown={}){
 const response=await fetch(url+'/rest/v1/rpc/jd_px',{method:'POST',headers:{Authorization:'Bearer '+serverKey,apikey:serverKey,'Content-Type':'application/json'},body:JSON.stringify({p_action:action,p_user:user,p_session:session,p_data:data}),signal:AbortSignal.timeout(8000)});
 const value=await response.json();if(!response.ok)throw new Error(value.code==='42501'?'access':value.message||'rpc');return value;
}
function config(){const ws=(Deno.env.get('LIVEKIT_URL')||'').trim(),key=(Deno.env.get('LIVEKIT_API_KEY')||'').trim(),secret=(Deno.env.get('LIVEKIT_API_SECRET')||'').trim();const host=new URL(ws);if(!key||!secret||host.protocol!=='wss:'||!host.hostname.endsWith('.livekit.cloud'))throw Error('config');return{ws,http:'https://'+host.host,key,secret};}
async function jwt(grant:Record<string,unknown>,identity?:string,name?:string){const cfg=config(),now=Math.floor(Date.now()/1000),part=(v:unknown)=>b64(enc.encode(JSON.stringify(v)));const value=part({alg:'HS256',typ:'JWT'})+'.'+part({iss:cfg.key,nbf:now-5,exp:now+90,...(identity?{sub:identity,name}:{}),video:grant});const key=await crypto.subtle.importKey('raw',enc.encode(cfg.secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);return value+'.'+b64(new Uint8Array(await crypto.subtle.sign('HMAC',key,enc.encode(value))));}
async function live(method:string,room:string,body:Record<string,unknown>={}){
 const token=await jwt({roomAdmin:true,room});const response=await fetch(config().http+'/twirp/livekit.RoomService/'+method,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({room,...body}),signal:AbortSignal.timeout(5000)});
 if(response.status===404&&['ListParticipants','DeleteRoom','RemoveParticipant'].includes(method))return {participants:[]};
 if(!response.ok)throw Error('voice-server');return await response.json();
}
async function reconcile(channel:string){const state=await rpc('inspect',null,null,{channel}),room='jdpx-'+state.room_key,allowed=new Set((state.connections||[]).map((x:{identity:string})=>x.identity));for(const key of state.retired||[]){await live('DeleteRoom','jdpx-'+key);await rpc('retired_done',null,null,{channel,room_key:key});}const people=(await live('ListParticipants',room)).participants||[];
 // Revoke everyone else before granting a new speaker: no overlapping transmitters.
 for(const person of people){if(!allowed.has(person.identity)){await live('RemoveParticipant',room,{identity:person.identity});continue;}if(person.identity!==state.speaker&&person.permission?.canPublish)await live('UpdateParticipant',room,{identity:person.identity,permission:{canSubscribe:true,canPublish:false,canPublishData:false,canPublishSources:[2]}});}
 const speaker=people.find((p:{identity:string})=>p.identity===state.speaker&&allowed.has(p.identity));if(speaker&&!speaker.permission?.canPublish)await live('UpdateParticipant',room,{identity:speaker.identity,permission:{canSubscribe:true,canPublish:true,canPublishData:false,canPublishSources:[2]}});
 if(!people.length&&!state.connections.length)await rpc('settled',null,null,{channel});
}
async function locked<T>(channel:string,work:()=>Promise<T>):Promise<T>{const lock=crypto.randomUUID();let acquired=false;for(let i=0;i<5;i++){if((await rpc('lock',null,null,{channel,lock})).ok){acquired=true;break;}await new Promise(r=>setTimeout(r,100));}if(!acquired)throw Error('busy');try{return await work();}finally{await rpc('unlock',null,null,{channel,lock}).catch(()=>{});}}
Deno.serve(async(req:Request)=>{
 if(req.method!=='POST')return json(405,{error:'Método indisponível.'});
 try{
 if(Number(req.headers.get('content-length')||0)>4096)return json(413,{error:'Pedido muito grande.'});const raw=await req.text();if(raw.length>4096)return json(413,{error:'Pedido muito grande.'});const body=JSON.parse(raw);
 const hook=req.headers.get('X-PX-Hook');if(hook){if(hook.length!==64||!(await rpc('hook_check',null,null,{nonce:hook})).ok)return json(403,{error:'Acesso indisponível.'});const rooms=(await rpc('rooms',null,null)).rooms||[];let failed=0;for(const channel of rooms){try{await locked(channel,()=>reconcile(channel));}catch{failed++;}}return json(failed?503:200,{ok:failed===0});}
 const auth=req.headers.get('Authorization')||'';if(!/^Bearer \S+$/.test(auth))return json(401,{error:'Entre novamente para usar PX.'});const userResponse=await fetch(url+'/auth/v1/user',{headers:{Authorization:auth,apikey:serverKey},signal:AbortSignal.timeout(6000)});if(!userResponse.ok)return json(401,{error:'Entre novamente para usar PX.'});const user=await userResponse.json();let session:string;try{const part=auth.slice(7).split('.')[1].replace(/-/g,'+').replace(/_/g,'/');session=JSON.parse(atob(part)).session_id;}catch{return json(401,{error:'Sessão inválida.'});}if(!uuid(user.id)||!uuid(session))return json(401,{error:'Sessão inválida.'});
 const action=String(body.action||''),valid=['list','private','group','accept','invite','remove','exit','close','connect','tick','claim','release','leave'];if(!valid.includes(action))return json(400,{error:'Ação inválida.'});
 const data={channel:body.channel,peer:body.peer,connection:body.connection,name:body.name,talking:body.talking===true};
 if(['list','private','group'].includes(action))return json(200,await rpc(action,user.id,session,data));
 if(!uuid(data.channel))return json(400,{error:'Canal inválido.'});
 const result=await locked(data.channel,async()=>{const value=await rpc(action,user.id,session,data);await reconcile(data.channel);return value;});
 if(action==='connect'){result.url=config().ws;result.token=await jwt({roomJoin:true,room:'jdpx-'+result.room_key,canSubscribe:true,canPublish:false,canPublishData:false,canPublishSources:['microphone']},result.connection,result.display_name);}
 return json(200,result);
 }catch(error){const message=error instanceof Error?error.message:'';if(message==='access')return json(403,{error:'Acesso encerrado. Confira amizade, convite e sessão.'});if(message==='busy')return json(409,{error:'Canal ocupado. Tente novamente.'});console.warn('PX request failed',message==='voice-server'?'voice-server':'request');return json(503,{error:'Não foi possível conectar ao rádio. Tente novamente.'});}
});
