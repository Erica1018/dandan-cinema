"""Install and pair only the explicitly selected TV. Never prints the pairing token."""
import argparse,json,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from setup_tv import DATA,validate_config

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb',type=Path,required=True)
    p.add_argument('--apk',type=Path,required=True)
    p.add_argument('--device',required=True,help='Explicit TV IPv4:ADB-port shown in developer settings')
    a=p.parse_args()
    c=validate_config(json.loads((DATA/'tv-access.json').read_text(encoding='utf-8')))
    host,sep,port=a.device.partition(':')
    if host!=c['tv_host'] or not sep or not port.isdigit() or not 1<=int(port)<=65535:
        raise SystemExit('Device must match the configured TV address and its ADB port.')
    adb=a.adb.resolve(strict=True);apk=a.apk.resolve(strict=True)
    def run(args):
        result=subprocess.run([str(adb),*args],capture_output=True,text=True,timeout=180)
        if result.returncode or 'error:' in (result.stdout+result.stderr).lower() or 'failure' in result.stdout.lower():
            raise SystemExit('TV operation failed. Check its on-screen authorization and connection. No credentials were printed.')
        return result.stdout.strip()
    run(['connect',a.device])
    if run(['-s',a.device,'get-state'])!='device':raise SystemExit('Authorize this computer on the TV, then run again.')
    run(['-s',a.device,'install','-r',str(apk)])
    result=run(['-s',a.device,'shell','am','start','-W','-n','io.github.erica1018.dandancinema/.MainActivity','--es','server',f"http://{c['host']}:{c['port']}",'--es','pair',c['token']])
    if 'Status: ok' not in result:raise SystemExit('App start not confirmed. Check the TV screen.')
    print('Installed and launch acknowledged. Verify the library and playback on the TV. / 安装启动已确认，请在电视上检查片单和播放。')

if __name__=='__main__':main()
