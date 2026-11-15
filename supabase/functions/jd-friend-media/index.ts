// Caller JWT is verified by the platform, and each file is authorized against the current session/friendship.
// Never return signed URLs or forward Storage's caching/redirect headers to the caller.
const headers = {"Cache-Control":"private, no-store, max-age=0", "Pragma":"no-cache", "Vary":"Authorization", "X-Content-Type-Options":"nosniff"};
Deno.serve(async (req:Request) => {
  const fail=(status:number)=>new Response("Arquivo indisponível",{status,headers});
  if(req.method!=="POST")return fail(405);
  const auth=req.headers.get("Authorization") ?? "";
  if(!/^Bearer \S+$/.test(auth))return fail(401);
  if(Number(req.headers.get("content-length") ?? "0")>2048)return fail(413);
  try {
    const text=await req.text();if(text.length>2048)return fail(413);
    const path=JSON.parse(text)?.path;
    if(typeof path!=="string" || !/^[a-f0-9-]{36}\/(avatars\/[a-f0-9-]{36}\.jpg|audio\/[a-f0-9-]{36}\.m4a)$/.test(path))return fail(400);
    const url=Deno.env.get("SUPABASE_URL")!;
    const anon=Deno.env.get("SUPABASE_ANON_KEY")!;
    const allowed=await fetch(url+"/rest/v1/rpc/jd_friend_media_access",{
      method:"POST",headers:{"Authorization":auth,"apikey":anon,"Content-Type":"application/json"},body:JSON.stringify({object_path:path}),signal:AbortSignal.timeout(10000)
    });
    if(!allowed.ok || await allowed.json()!==true)return fail(403);
    const serverKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const file=await fetch(url+"/storage/v1/object/authenticated/jd-friend-media/"+path,{
      headers:{"Authorization":"Bearer "+serverKey,"apikey":serverKey},signal:AbortSignal.timeout(15000)
    });
    if(!file.ok)return fail(404);
    const type=file.headers.get("Content-Type")?.split(";")[0] ?? "";
    if(!["image/jpeg","audio/mp4"].includes(type))return fail(415);
    const bytes=await file.arrayBuffer();if(bytes.byteLength>2097152)return fail(413);
    return new Response(bytes,{status:200,headers:{...headers,"Content-Type":type}});
  }catch{return fail(503);}
});
