"""Obtain synthetic accounts only through GitHub's short-lived OIDC identity. Never print credentials."""
import os,json,urllib.request
from pathlib import Path
url=os.environ['ACTIONS_ID_TOKEN_REQUEST_URL']+'&audience=jhondriver-px-qa'
with urllib.request.urlopen(urllib.request.Request(url,headers={'Authorization':'Bearer '+os.environ['ACTIONS_ID_TOKEN_REQUEST_TOKEN']}),timeout=20)as r:token=json.load(r)['value']
with urllib.request.urlopen(urllib.request.Request('https://hyoaomusfpeccdchgoik.supabase.co/functions/v1/jd-px-qa',headers={'Authorization':'Bearer '+token}),timeout=20)as r:data=json.load(r)
selected=[data['users'][i]for i in ([0,1]if os.environ['PX_API_LEVEL']=='33'else[3,4])]
path=Path(os.environ['RUNNER_TEMP'])/'px-qa.json';path.write_text(json.dumps({'users':selected,'password':data['password']}));path.chmod(0o600)
print('Synthetic voice fixture obtained through verified CI identity')
