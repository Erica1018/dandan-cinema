"""Explicit local network pairing configuration; no discovery or device changes."""
import argparse
import ipaddress
import json
from pathlib import Path
import secrets
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent/'watchroom'))
from settings import DATA

PRIVATE=[ipaddress.ip_network(n) for n in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16')]

def private_ip(value):
    address=ipaddress.ip_address(value)
    if not any(address in network for network in PRIVATE):
        raise ValueError('Use an explicit private IPv4 address / 请使用局域网 IPv4 地址')
    return str(address)

def validate_config(config):
    private_ip(config['host']);private_ip(config['tv_host'])
    if type(config['port']) is not int or not 1024 <= config['port'] <= 65535:
        raise ValueError('Invalid TV service port')
    if not isinstance(config['token'],str) or len(config['token'])<32:
        raise ValueError('Invalid pairing configuration')
    return config

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host',required=True,help="This computer's private IPv4 address")
    parser.add_argument('--tv-host',required=True,help="Your TV's private IPv4 address")
    parser.add_argument('--port',type=int,default=18777)
    args=parser.parse_args()
    path=DATA/'tv-access.json'
    if path.exists():
        current=validate_config(json.loads(path.read_text(encoding='utf-8')))
        if (current['host'],current['tv_host'],current['port'])==(args.host,args.tv_host,args.port):
            print('Existing pairing retained / 已保留现有配对。');return
        raise SystemExit('Existing pairing differs. Stop the service and back up its configuration before explicitly replacing it. / 已有配对不同，请停止服务并备份后再调整。')
    config=validate_config({'host':args.host,'tv_host':args.tv_host,'port':args.port,'token':secrets.token_urlsafe(32)})
    DATA.mkdir(parents=True,exist_ok=True)
    with path.open('x',encoding='utf-8') as f:json.dump(config,f,indent=2)
    print('TV configuration saved. Restart run.py, then run watchroom-tv/install.py. / 配置已保存，请重启服务并安装电视端。')

if __name__=='__main__':main()
