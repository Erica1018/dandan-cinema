"""Build a small native TV APK using the installed Android SDK and a JDK."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import zipfile

p=argparse.ArgumentParser()
p.add_argument('--jdk',type=Path,required=True)
p.add_argument('--sdk',type=Path,required=True)
p.add_argument('--work',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
src=Path(__file__).resolve().parent
runtime=src.parent/'runtime'/'watchroom'
runtime.mkdir(parents=True,exist_ok=True)
build=a.sdk/'build-tools'/'36.0.0'
android=a.sdk/'platforms'/'android-34'/'android.jar'
for directory in ('generated','classes','dex'):(a.work/directory).mkdir(parents=True,exist_ok=True)
def run(args):subprocess.run([str(x) for x in args],check=True)
resource=a.work/'resources.apk'
run([build/'aapt.exe','package','-f','-m','-J',a.work/'generated','-M',src/'AndroidManifest.xml','-S',src/'res','-I',android,'-F',resource])
java_sources=[*src.rglob('*.java'),*(a.work/'generated').rglob('*.java')]
bootclasspath=str(build/'core-lambda-stubs.jar')+os.pathsep+str(android)
run([a.jdk/'bin'/'javac.exe','-J-Duser.language=en','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',bootclasspath,'-d',a.work/'classes',*java_sources])
run([a.jdk/'bin'/'java.exe','-cp',build/'lib'/'d8.jar','com.android.tools.r8.D8','--min-api','21','--lib',android,'--output',a.work/'dex',*(a.work/'classes').rglob('*.class')])
with zipfile.ZipFile(resource,'a') as z:z.write(a.work/'dex'/'classes.dex','classes.dex')
aligned=a.work/'aligned.apk'
run([build/'zipalign.exe','-f','4',resource,aligned])
keys=runtime/'tv-signing.json'
keystore=runtime/'tv-release.jks'
if not keys.exists():
    if keystore.exists():raise RuntimeError('Signing key exists without password record; preserve it.')
    keys.write_text(json.dumps({'password':secrets.token_urlsafe(32)}),encoding='utf-8')
os.environ['WATCHROOM_SIGN_PASSWORD']=json.loads(keys.read_text(encoding='utf-8'))['password']
if not keystore.exists():run([a.jdk/'bin'/'keytool.exe','-genkeypair','-keystore',keystore,'-alias','watchroom','-storepass:env','WATCHROOM_SIGN_PASSWORD','-keypass:env','WATCHROOM_SIGN_PASSWORD','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Dandan Cinema Community'])
a.output.parent.mkdir(parents=True,exist_ok=True)
run([a.jdk/'bin'/'java.exe','-jar',build/'lib'/'apksigner.jar','sign','--ks',keystore,'--ks-key-alias','watchroom','--ks-pass','env:WATCHROOM_SIGN_PASSWORD','--key-pass','env:WATCHROOM_SIGN_PASSWORD','--out',a.output,aligned])
run([a.jdk/'bin'/'java.exe','-jar',build/'lib'/'apksigner.jar','verify','--verbose',a.output])
print('TV APK built and signature verified. Bytes:',a.output.stat().st_size)
