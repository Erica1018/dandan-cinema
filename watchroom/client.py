"""Vendor-neutral client for the local Dandan Cinema API."""
import json
import os
from urllib.parse import urlsplit
import urllib.request
import urllib.error

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('Local API redirects are not allowed')

class Client:
    def __init__(self, base=None):
        self.base=(base or os.environ.get('DANDAN_URL','http://127.0.0.1:18776')).rstrip('/')
        url=urlsplit(self.base)
        if url.scheme!='http' or url.hostname not in ('127.0.0.1','localhost') or url.username or url.password or url.path or url.query or url.fragment:
            raise ValueError('DANDAN_URL must be a loopback HTTP origin')
        self.http=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())

    def request(self, path, body=None, token=None):
        headers={'Content-Type':'application/json'}
        if token:headers['X-Watchroom-Token']=token
        raw=None if body is None else json.dumps(body,ensure_ascii=False).encode('utf-8')
        req=urllib.request.Request(self.base+path,data=raw,headers=headers)
        try:
            with self.http.open(req,timeout=10) as response:
                payload=response.read(2_000_001)
                if len(payload)>2_000_000:raise ValueError('Library response too large')
                return json.loads(payload)
        except urllib.error.HTTPError as error:
            with error:message=json.loads(error.read(8192)).get('error','Local API request failed')
            raise APIError(error.code,message) from None
        except urllib.error.URLError:
            raise RuntimeError('Start Dandan Cinema with python run.py / 请先启动蛋蛋影院') from None

    def state(self):return self.request('/api/state')

    @staticmethod
    def public_item(item):
        fields=('id','title','category','status','note','tags','synopsis','douban','preparation')
        result={k:item.get(k) for k in fields}
        result['sources']=[{k:s.get(k) for k in ('id','kind','label')} for s in item.get('sources',[])]
        result['has_prepared_media']=bool(item.get('playback',{}).get('asset'))
        return result

    def list(self, query=''):
        query=query.casefold()
        return [self.public_item(x) for x in self.state()['items'] if query in (x['title']+' '+x.get('synopsis','')+' '+x.get('note','')).casefold()]

    def get(self, item_id):return self.public_item(self._item(self.state(),item_id))

    @staticmethod
    def _item(state,item_id):
        item=next((dict(x) for x in state['items'] if x['id']==item_id),None)
        if item is None:raise ValueError('Unknown item ID / 未找到影片')
        return item

    def update(self, transform):
        for attempt in range(4):
            state=self.state();item=transform(state)
            try:
                saved=self.request('/api/item',{'revision':state['revision'],'item':item},state['csrf'])['item']
                return self.get(saved['id'])
            except APIError as error:
                if error.status!=409 or attempt==3:raise

    def add(self,title,category='电影',note=''):
        if not title.strip():raise ValueError('A title is required')
        def transform(state):
            same=next((x for x in state['items'] if x['title'].strip().casefold()==title.strip().casefold()),None)
            return same or {'title':title,'category':category,'status':'want','note':note,'sources':[], 'preparation':{'stage':'saved','message':''}}
        return self.update(transform)

    def modify(self,item_id,changes):
        allowed={'synopsis','douban','category','tags','status','note','preparation','playback','files'}
        if not isinstance(changes,dict) or set(changes)-allowed:raise ValueError('Unsupported update fields')
        return self.update(lambda state:self._item(state,item_id)|changes)

    def source(self,item_id,url,kind='direct',label='Video'):
        def transform(state):
            item=self._item(state,item_id);sources=list(item.get('sources',[]))
            if not any(s['url']==url and s['kind']==kind for s in sources):sources.append({'kind':kind,'label':label,'url':url})
            item['sources']=sources;return item
        return self.update(transform)

    def preview_delete(self,item_id):
        state=self.state()
        return self.request('/api/delete-preview',{'id':item_id,'revision':state['revision']},state['csrf'])

    def delete(self,item_id,revision,confirmation):
        state=self.state()
        return self.request('/api/delete',{'id':item_id,'revision':revision,'confirmation':confirmation},state['csrf'])

class APIError(RuntimeError):
    def __init__(self,status,message):
        super().__init__(message);self.status=status
