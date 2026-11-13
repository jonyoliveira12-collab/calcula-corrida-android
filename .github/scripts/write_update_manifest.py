"""Create public update metadata from the actual signed APK, never from supplied credentials."""
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

apk = Path("app/build/outputs/apk/release/app-release.apk")
build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "35.0.0"
badging = subprocess.check_output([str(build_tools / "aapt"), "dump", "badging", str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
assert package, "APK package metadata missing"
certs = subprocess.check_output([str(build_tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
cert = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", certs)
assert cert, "APK signer missing"
repository = os.environ["GITHUB_REPOSITORY"]
tag = "apk-build-" + os.environ["GITHUB_RUN_NUMBER"]
manifest = {"channel": "stable", "packageName": package[1], "versionCode": int(package[2]), "versionName": package[3],
            "certificateSha256": cert[1].lower(), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "apkUrl": f"https://github.com/{repository}/releases/download/{tag}/{apk.name}"}
Path("update.json").write_text(json.dumps(manifest, indent=2) + "\n")
