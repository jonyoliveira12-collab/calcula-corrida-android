"""Fail closed if this manual edition contains the retired invasive capabilities."""
import json
import os
import subprocess
import sys
import zipfile
from pathlib import Path

apk=Path(sys.argv[1])
aapt=Path(os.environ['ANDROID_HOME'])/'build-tools'/'35.0.0'/'aapt'
permissions=subprocess.check_output([str(aapt),'dump','permissions',str(apk)],text=True)
manifest=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
for prohibited in ['SYSTEM_ALERT_WINDOW','BIND_ACCESSIBILITY_SERVICE','READ_EXTERNAL_STORAGE','READ_MEDIA_IMAGES','QUERY_ALL_PACKAGES','REQUEST_INSTALL_PACKAGES','MEDIA_PROJECTION','android.accessibilityservice.AccessibilityService']:
    assert prohibited not in permissions+manifest, 'Unexpected capability: '+prohibited
with zipfile.ZipFile(apk) as z:
    dex=b''.join(z.read(name) for name in z.namelist() if name.endswith('.dex'))
    for name in ['OfferAccessibilityService','OfferTagView','OverlayView','AiOfferClient']:
        assert ('Lbr/com/calculacorrida/'+name+';').encode() not in dex, 'Retired implementation shipped: '+name
print(json.dumps({'result':'passed','scope':'manifest permissions and retired app classes','permissions':permissions.splitlines()},indent=2))
