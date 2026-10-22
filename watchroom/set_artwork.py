"""Attach artwork data to an existing item via the shared optimistic-concurrency API."""
import argparse
from pathlib import Path
from settings import DATA
import hashlib
import io
import json
import os
from PIL import Image, ImageOps
from client import Client

ROOT=Path(__file__).resolve().parent.parent

def prepare(image,folder,size):
    picture=ImageOps.fit(image.convert('RGB'),size,method=Image.Resampling.LANCZOS)
    output=io.BytesIO();picture.save(output,'JPEG',quality=86,optimize=True)
    raw=output.getvalue()
    if len(raw)>1000000:
        raise ValueError('Artwork exceeds the paired image limit')
    asset=hashlib.sha256(raw).hexdigest();target=folder/(asset+'.jpg')
    if target.exists():
        if target.read_bytes()!=raw:raise RuntimeError('Existing artwork hash mismatch')
    else:
        temporary=target.with_suffix('.jpg.tmp');temporary.write_bytes(raw);os.replace(temporary,target)
    return asset

def main():
    parser=argparse.ArgumentParser();parser.add_argument('id');parser.add_argument('--image',type=Path,required=True);parser.add_argument('--credit',default='')
    args=parser.parse_args()
    client=Client();existing=client._item(client.state(),args.id)
    if existing is None:raise ValueError('Unknown item; artwork does not create a new movie')
    folder=DATA/'artwork';folder.mkdir(parents=True,exist_ok=True)
    with Image.open(args.image) as image:
        artwork={'hero':prepare(image,folder,(1600,900)),'thumbnail':prepare(image,folder,(568,228)),'credit':args.credit}
    def transform(state):
        current=next((dict(x) for x in state['items'] if x['id']==args.id),None)
        if current is None or current['title']!=existing['title']:raise ValueError('Movie identity changed; recheck artwork before publishing')
        current['artwork']=artwork;return current
    client.update(transform)
    receipt=client._item(client.state(),args.id)
    if receipt.get('artwork')!=artwork:raise RuntimeError('Artwork readback differs')
    print(json.dumps({'id':args.id,'artwork':artwork,'verified':True},ensure_ascii=True))

if __name__=='__main__':main()
