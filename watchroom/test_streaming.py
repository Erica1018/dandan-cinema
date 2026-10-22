import importlib.util
import json
import tempfile
import threading
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import urllib.request
import urllib.error
from pathlib import Path
from http.server import ThreadingHTTPServer

def load(name):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(name+'.py'))
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

class Streaming(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        self.m=load('tv_server');self.m.MEDIA=self.root
        self.data=b'0123456789'*10000
        (self.root/'test-stream.mp4').write_bytes(self.data)
        self.m.STATE=self.root/'library.json'
        self.m.STATE.write_text(json.dumps({'revision':1,'items':[{'id':'one','sources':[{'kind':'local','url':'C:/private/movie.mp4'}],'preparation':{'stage':'saved'}}]}))
        self.s=ThreadingHTTPServer(('127.0.0.1',0),self.m.Handler)
        self.port=self.s.server_port;self.s.config={'host':'127.0.0.1','tv_host':'127.0.0.1','port':self.port,'token':'test-only-token'}
        threading.Thread(target=self.s.serve_forever,daemon=True).start()
        self.http=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def tearDown(self):
        self.s.shutdown();self.s.server_close();self.tmp.cleanup()
    def get(self,path,headers=None,method='GET',auth=True):
        h={'X-Watchroom-Pair':'test-only-token'} if auth else {}
        h.update(headers or {})
        return self.http.open(urllib.request.Request(f'http://127.0.0.1:{self.port}'+path,headers=h,method=method))
    def test_full_range_seek_suffix_and_head(self):
        with self.get('/media/test-stream.mp4') as r:self.assertEqual(r.read(),self.data)
        for field,expected in [('bytes=10-19',self.data[10:20]),('bytes=99995-',self.data[99995:]),('bytes=-7',self.data[-7:])]:
            with self.get('/media/test-stream.mp4',{'Range':field}) as r:
                self.assertEqual(r.status,206);self.assertEqual(r.read(),expected)
        with self.get('/media/test-stream.mp4',method='HEAD') as r:
            self.assertEqual(int(r.headers['Content-Length']),len(self.data));self.assertEqual(r.read(),b'')
    def test_auth_invalid_range_and_file_boundary(self):
        for path,h,auth,code in [('/media/test-stream.mp4',{},False,403),('/media/../library.json',{},True,404),('/media/test-stream.mp4',{'Range':'bytes=999999-'},True,416),('/media/test-stream.mp4',{'Range':'bytes=0-1,3-4'},True,416),('/media/test-stream.mp4',{'Host':'evil.test'},True,403)]:
            with self.assertRaises(urllib.error.HTTPError) as cm:self.get(path,h,auth=auth)
            self.assertEqual(cm.exception.code,code)
            cm.exception.close()
    def test_private_paths_not_exported(self):
        with self.get('/library') as r:
            text=r.read().decode();self.assertNotIn('C:/private',text);self.assertNotIn('test-only-token',text)
    def test_cannot_publish_unverified_media(self):
        m=load('server');m.PROJECT=self.root;m.STATE=self.root/'runtime/watchroom/library.json'
        item={'title':'Movie','preparation':{'stage':'ready'}}
        with self.assertRaises(ValueError):m.clean_item(item)
        asset='a'*64;(self.root/'runtime/watchroom/media').mkdir(parents=True)
        (self.root/'runtime/watchroom/media'/f'{asset}.mp4').write_bytes(b'fixture')
        item['playback']={'asset':asset}
        item['preparation']['checks']={x:True for x in ('identity','complete','original_audio','bilingual')}
        with self.assertRaises(ValueError):m.clean_item(item)
        item['preparation']['evidence']='Synthetic test only'
        self.assertEqual(m.clean_item(item)['preparation']['stage'],'ready')
        item['preparation']['checks']['bilingual']=False
        self.assertEqual(m.clean_item(item)['preparation']['stage'],'ready')
        item['preparation']['checks']['identity']=False
        with self.assertRaises(ValueError):m.clean_item(item)
    def test_srt_timing(self):
        m=load('prepare_media');srt=self.root/'one.srt'
        srt.write_text('1\n00:00:01,200 --> 00:00:03,400\n你好\nHello\n\n2\n00:00:04,000 --> 00:00:05,000\nNext\n',encoding='utf-8')
        result=m.cues(srt);self.assertEqual(len(result),2);self.assertEqual(result[0]['start'],1200);self.assertEqual(result[0]['end'],3400);self.assertIn('你好',result[0]['text'])

if __name__=='__main__':unittest.main()
