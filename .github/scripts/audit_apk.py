"""Check declared automatic-reader scope. This does NOT certify bank or Play Protect acceptance."""
import os
import subprocess
import sys
import zipfile
from pathlib import Path
apk=Path(sys.argv[1])
aapt=Path(os.environ['ANDROID_HOME'])/'build-tools'/'35.0.0'/'aapt'
permissions=subprocess.check_output([str(aapt),'dump','permissions',str(apk)],text=True)
manifest=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
assert 'SYSTEM_ALERT_WINDOW' in permissions
assert 'BIND_ACCESSIBILITY_SERVICE' in manifest
for unwanted in ['READ_SMS','READ_CONTACTS','READ_CALL_LOG','QUERY_ALL_PACKAGES','REQUEST_INSTALL_PACKAGES','MANAGE_EXTERNAL_STORAGE']:
    assert unwanted not in permissions, unwanted
config=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'res/xml/accessibility_service_config.xml'],text=True)
assert 'packageNames' in config and 'com.ubercab.driver' in config and 'com.app99.driver' in config
assert 'com.taxis99' not in config, 'The 99 passenger app must not be read'
with zipfile.ZipFile(apk) as z:
    dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
    assert b'Lbr/com/calculacorrida/OfferAccessibilityService;' in dex
    assert b'Lbr/com/calculacorrida/ManualOfferActivity;' not in dex
print('Automatic reader present, allowlist configured, manual-photo flow absent, unrelated permissions absent.')
