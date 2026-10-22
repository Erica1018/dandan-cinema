import importlib.util,json,hashlib,tempfile,threading,unittest,urllib.request,urllib.error
from pathlib import Path
from http.server import ThreadingHTTPServer

HERE=Path(__file__).parent
def module(name):
    spec=importlib.util.spec_from_file_location('candidate_'+name,HERE/f'{name}.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

class ArtworkTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.folder=self.root/'runtime/watchroom/artwork';self.folder.mkdir(parents=True)
        self.data=b'\xff\xd8\xff\xe0artwork-fixture';self.asset=hashlib.sha256(self.data).hexdigest();(self.folder/(self.asset+'.jpg')).write_bytes(self.data)
        self.m=module('tv_server');self.m.PROJECT=self.root;self.m.STATE=self.root/'runtime/watchroom/library.json'
        self.m.STATE.write_text(json.dumps({'revision':1,'items':[{'id':'one','title':'one','sources':[],'tags':['system:test'],'artwork':{'hero':self.asset,'thumbnail':self.asset,'credit':'Original cover'}}]}))
        self.http=urllib.request.build_opener(urllib.request.ProxyHandler({}));self.s=ThreadingHTTPServer(('127.0.0.1',0),self.m.Handler);self.s.config={'host':'127.0.0.1','tv_host':'127.0.0.1','port':self.s.server_port,'token':'fixture'};threading.Thread(target=self.s.serve_forever,daemon=True).start()
    def tearDown(self):self.s.shutdown();self.s.server_close();self.tmp.cleanup()
    def request(self,path,auth=True,method='GET'):
        return self.http.open(urllib.request.Request(f'http://127.0.0.1:{self.s.server_port}'+path,headers={'X-Watchroom-Pair':'fixture'} if auth else {},method=method))
    def test_paired_art_get_head_and_library_boundary(self):
        with self.request('/art/'+self.asset+'.jpg') as r:self.assertEqual(r.read(),self.data);self.assertEqual(r.headers['Content-Type'],'image/jpeg')
        with self.request('/art/'+self.asset+'.jpg',method='HEAD') as r:self.assertEqual(r.read(),b'');self.assertEqual(int(r.headers['Content-Length']),len(self.data))
        with self.request('/library') as r:
            raw=r.read();data=json.loads(raw);self.assertEqual(data['items'][0]['tags'],['system:test']);self.assertEqual(data['items'][0]['artwork']['hero'],'/art/'+self.asset+'.jpg');self.assertNotIn(str(self.root).encode(),raw)
    def test_auth_and_arbitrary_paths_rejected(self):
        for path,auth,status in [('/art/'+self.asset+'.jpg',False,403),('/art/../library.json',True,404),('/art/http://other/image.jpg',True,404),('/art/'+self.asset+'.png',True,404),('/art/'+'a'*64+'.jpg',True,404)]:
            with self.assertRaises(urllib.error.HTTPError) as error:self.request(path,auth)
            self.assertEqual(error.exception.code,status);error.exception.close()
    def test_metadata_preserved_and_rename_clears_art(self):
        m=module('server');m.PROJECT=self.root;m.STATE=self.root/'runtime/watchroom/library.json'
        art={'hero':self.asset,'thumbnail':self.asset,'credit':'Original cover'}
        old=m.clean_item({'title':'one','artwork':art,'sources':[{'kind':'platform','label':'source','url':'https://example.invalid/movie'}]})
        saved=m.clean_item({'title':'one','sources':old['sources']},old)
        self.assertEqual(saved['artwork'],art);self.assertEqual(saved['sources'],old['sources'])
        renamed=m.clean_item({**old,'title':'different'},old);self.assertEqual(renamed['artwork'],{})
        with self.assertRaises(ValueError):m.clean_item({'title':'bad','artwork':{'hero':'../private','thumbnail':self.asset}})
    def test_oversized_art_and_duplicate_source_ids(self):
        m=module('server');m.PROJECT=self.root;m.STATE=self.root/'runtime/watchroom/library.json'
        old=m.clean_item({'title':'one','sources':[{'kind':'platform','label':'source','url':'https://example.invalid/movie'}]})
        new=m.clean_item({**old,'sources':old['sources']*2},old);self.assertEqual(new['sources'][0]['id'],old['sources'][0]['id']);self.assertNotEqual(new['sources'][0]['id'],new['sources'][1]['id'])
        (self.folder/(self.asset+'.jpg')).write_bytes(b'x'*1000001)
        with self.assertRaises(ValueError):m.clean_artwork({'hero':self.asset,'thumbnail':self.asset})
        with self.assertRaises(urllib.error.HTTPError) as error:self.request('/art/'+self.asset+'.jpg')
        self.assertEqual(error.exception.code,404);error.exception.close()

if __name__=='__main__':unittest.main()
