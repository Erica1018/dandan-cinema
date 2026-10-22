"""JSON CLI for any local assistant. No direct library file writes."""
import argparse
import json
from pathlib import Path
import sys
from client import Client

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',help='Local editor origin; default DANDAN_URL or http://127.0.0.1:18776')
    sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('health')
    ls=sub.add_parser('list');ls.add_argument('--query',default='')
    get=sub.add_parser('get');get.add_argument('id')
    add=sub.add_parser('add');add.add_argument('title');add.add_argument('--category',default='电影');add.add_argument('--note',default='')
    describe=sub.add_parser('describe');describe.add_argument('id');describe.add_argument('--metadata',type=Path,required=True)
    status=sub.add_parser('status');status.add_argument('id');status.add_argument('status',choices=['want','watching','watched','inbox'])
    stage=sub.add_parser('stage');stage.add_argument('id');stage.add_argument('stage',choices=['saved','searching','needs_access','preparing','unavailable']);stage.add_argument('message',nargs='?',default='')
    source=sub.add_parser('source');source.add_argument('id');source.add_argument('url');source.add_argument('--kind',choices=['direct','platform','cloud','local'],default='direct');source.add_argument('--label',default='Video')
    files=sub.add_parser('files');files.add_argument('id');files.add_argument('--manifest',type=Path,required=True)
    publish=sub.add_parser('publish');publish.add_argument('id');publish.add_argument('--asset',required=True);publish.add_argument('--evidence',type=Path,required=True)
    preview=sub.add_parser('delete-preview');preview.add_argument('id')
    delete=sub.add_parser('delete');delete.add_argument('id');delete.add_argument('--revision',type=int,required=True);delete.add_argument('--confirmation',required=True)
    args=parser.parse_args();client=Client(args.url)
    if args.command=='health':result=client.request('/api/health')
    elif args.command=='list':result=client.list(args.query)
    elif args.command=='get':result=client.get(args.id)
    elif args.command=='add':result=client.add(args.title,args.category,args.note)
    elif args.command=='describe':
        data=json.loads(args.metadata.read_text(encoding='utf-8-sig'))
        if not isinstance(data,dict) or set(data)-{'synopsis','douban','category','tags','note'}:raise ValueError('Only metadata fields are allowed')
        result=client.modify(args.id,data)
    elif args.command=='status':result=client.modify(args.id,{'status':args.status})
    elif args.command=='stage':result=client.modify(args.id,{'preparation':{'stage':args.stage,'message':args.message}})
    elif args.command=='source':result=client.source(args.id,args.url,args.kind,args.label)
    elif args.command=='files':result=client.modify(args.id,{'files':json.loads(args.manifest.read_text(encoding='utf-8-sig'))})
    elif args.command=='publish':
        evidence=json.loads(args.evidence.read_text(encoding='utf-8-sig'))
        result=client.modify(args.id,{'preparation':{**evidence,'stage':'ready'},'playback':{'asset':args.asset}})
    elif args.command=='delete-preview':result=client.preview_delete(args.id)
    else:result=client.delete(args.id,args.revision,args.confirmation)
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':
    try:main()
    except Exception as error:
        print(json.dumps({'error':str(error)},ensure_ascii=False),file=sys.stderr);sys.exit(1)
