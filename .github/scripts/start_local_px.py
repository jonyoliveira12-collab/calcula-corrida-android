"""Disposable emulator-only RTC server. Credentials never authorize production."""
import base64,hashlib,hmac,json,os,socket,subprocess,time
from pathlib import Path
root=Path(os.environ['RUNNER_TEMP'])/'px-local'
root.mkdir(exist_ok=True)
subprocess.run(['gh','release','download','v1.13.11','--repo','livekit/livekit','--pattern','livekit_1.13.11_linux_amd64.tar.gz','--pattern','checksums.txt','--dir',str(root)],check=True)
archive=root/'livekit_1.13.11_linux_amd64.tar.gz'
expected=next(line.split()[0] for line in (root/'checksums.txt').read_text().splitlines() if line.endswith(archive.name))
assert hashlib.sha256(archive.read_bytes()).hexdigest()==expected
subprocess.run(['tar','-xzf',str(archive),'-C',str(root)],check=True)
secret='local-emulator-only-secret-never-production'
config=root/'server.yaml'
# The emulator's 10.0.2.2 is a client-side alias, not a host ICE interface.
# Advertise the runner's real NIC so ICE packets can reach the SFU.
with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as route:
    route.connect(('8.8.8.8',53));host_ip=route.getsockname()[0]
config.write_text('port: 7880\nbind_addresses: ["127.0.0.1", "'+host_ip+'"]\nrtc:\n  tcp_port: 7881\n  udp_port: 7882\n  node_ip: '+host_ip+'\n  use_external_ip: false\nkeys:\n  devkey: '+secret+'\n')
log=(root/'server.log').open('wb')
subprocess.Popen([str(root/'livekit-server'),'--config',str(config)],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
def encode(value):return base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).decode().rstrip('=')
def token(identity):
    unsigned=encode({'alg':'HS256','typ':'JWT'})+'.'+encode({'iss':'devkey','sub':identity,'name':identity,'nbf':int(time.time())-30,'exp':int(time.time())+3600,'video':{'roomJoin':True,'room':'px-local-test','canPublish':True,'canSubscribe':True,'canPublishData':False}})
    return unsigned+'.'+base64.urlsafe_b64encode(hmac.new(secret.encode(),unsigned.encode(),hashlib.sha256).digest()).decode().rstrip('=')
(root/'fixture.json').write_text(json.dumps({'url':'ws://10.0.2.2:7880','a':token('sender'),'b':token('receiver')}))
print('Isolated local RTC server prepared; no production accounts or secrets used.')
