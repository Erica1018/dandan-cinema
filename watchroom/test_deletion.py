import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from http.server import ThreadingHTTPServer
import urllib.request
import urllib.error
import resources
import server
import tv_server


class Deletion(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.root=Path(self.tmp.name)
        self.base=self.root/'runtime/watchroom'
        self.base.mkdir(parents=True)
        self.patches=[patch.object(server,'STATE',self.base/'library.json'),patch.object(server,'PROJECT',self.root)]
        for p in self.patches:p.start()
        self.asset='a'*64
        self.item={'id':'movie','title':'隔离验证影片','sources':[], 'files':['sources/movie.mkv'],
                   'preparation':{'stage':'ready'},'playback':{'asset':self.asset}}
        for name,raw in [('sources/movie.mkv',b'original'),(f'media/{self.asset}.mp4',b'playable'),(f'media/{self.asset}.json',b'{}')]:
            file=self.base/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(raw)
        server.save_state({'revision':1,'items':[self.item]})

    def tearDown(self):
        for p in reversed(self.patches):p.stop()
        self.tmp.cleanup()

    def preview(self,id='movie'):
        state=server.read_state()
        return server.manage_delete('/api/delete-preview',{'revision':state['revision'],'id':id})

    def erase(self,preview=None,id='movie'):
        if preview is None:
            code,preview=self.preview(id);self.assertEqual(code,200)
        return server.manage_delete('/api/delete',{'revision':preview['revision'],'id':id,'confirmation':preview['confirmation']})

    def test_coupled_delete_only_owned_files_and_readback(self):
        untouched=self.base/'sources/another.mkv';untouched.write_bytes(b'other')
        code,preview=self.preview();self.assertEqual((code,preview['file_count'],preview['bytes']),(200,3,18))
        self.assertNotIn('path',json.dumps(preview));self.assertEqual(self.erase(preview)[0],200)
        self.assertEqual(server.read_state()['items'],[]);self.assertFalse((self.base/'sources/movie.mkv').exists())
        self.assertTrue(untouched.exists())

    def test_same_request_retries_return_receipt(self):
        _,preview=self.preview();first=self.erase(preview);second=self.erase(preview)
        self.assertEqual(first,second);self.assertEqual(first[1]['bytes'],18)

    def test_shared_resources_survive_until_last_reference(self):
        state=server.read_state();state['items'].append({**self.item,'id':'two','title':'另一条共用记录'});server.save_state(state)
        _,p=self.preview();self.assertEqual(p['file_count'],0);self.assertEqual(p['shared_files'],3)
        self.assertEqual(self.erase(p)[0],200);self.assertTrue((self.base/'sources/movie.mkv').exists())
        self.assertEqual(self.erase(id='two')[0],200);self.assertFalse((self.base/'sources/movie.mkv').exists())

    def test_file_replacement_requires_new_confirmation(self):
        _,p=self.preview();(self.base/'sources/movie.mkv').write_bytes(b'replacement')
        self.assertEqual(self.erase(p)[0],409);self.assertEqual(len(server.read_state()['items']),1)
        self.assertTrue((self.base/f'media/{self.asset}.mp4').exists())

    def test_revision_conflict_keeps_card_and_files(self):
        _,p=self.preview();s=server.read_state();s['revision']+=1;server.save_state(s)
        self.assertEqual(self.erase(p)[0],409);self.assertTrue((self.base/'sources/movie.mkv').exists())

    def test_partial_failure_is_retryable_without_false_success(self):
        original=Path.unlink
        def fail(path,*args,**kwargs):
            if path.name==self.asset+'.mp4':raise PermissionError('fixture lock')
            return original(path,*args,**kwargs)
        with patch.object(Path,'unlink',fail):
            code,result=self.erase();self.assertEqual(code,409);self.assertTrue(result['partial'])
        current=server.read_state();self.assertEqual(len(current['items']),1)
        self.assertEqual(current['items'][0]['deletion']['status'],'retry')
        self.assertFalse((self.base/'sources/movie.mkv').exists())
        code,result=self.erase();self.assertEqual(code,200);self.assertEqual(result['bytes'],18)

    def test_active_stream_and_preparation_are_protected(self):
        with resources.open_stream(self.base/f'media/{self.asset}.mp4',server.LOCK):
            self.assertEqual(self.preview()[0],409)
        s=server.read_state();s['items'][0]['preparation']['stage']='preparing';server.save_state(s)
        self.assertEqual(self.preview()[0],409);self.assertTrue((self.base/'sources/movie.mkv').exists())

    def test_paths_and_reparse_points_are_rejected(self):
        for name in ['../private.mkv','sources/../../private.mkv','C:/private.mkv','sources\\private.mkv',
                     'tv-access.json','media/test-stream.mp4','sources/private.py','artwork/../private.jpg']:
            with self.assertRaises(ValueError):resources.checked(self.base,name)
        actual=Path.lstat
        class Junction:
            st_mode=0o40755
            st_file_attributes=0x400
        def fake(path,*args,**kwargs):
            return Junction() if path==self.base/'sources' else actual(path,*args,**kwargs)
        with patch.object(Path,'lstat',fake):
            with self.assertRaises(ValueError):resources.checked(self.base,'sources/movie.mkv')

    def test_asset_registry_resolves_original_and_is_cleaned(self):
        item={**self.item,'files':[]};registry=self.base/f'media/{self.asset}.files.json'
        registry.write_text(json.dumps({'asset':self.asset,'files':['sources/movie.mkv']}))
        s=server.read_state();s['items']=[item];server.save_state(s)
        _,p=self.preview();self.assertEqual(p['file_count'],4)
        self.assertEqual(self.erase(p)[0],200);self.assertFalse(registry.exists());self.assertFalse((self.base/'sources/movie.mkv').exists())

    def test_saved_online_card_has_zero_disk_bytes(self):
        s=server.read_state();s['items']=[{'id':'online','title':'在线','sources':[],'preparation':{'stage':'saved'}}];server.save_state(s)
        _,p=self.preview('online');self.assertEqual(p['bytes'],0);self.assertEqual(self.erase(p,'online')[0],200)
        self.assertTrue((self.base/'sources/movie.mkv').exists())

    def test_old_editor_updates_preserve_registered_files(self):
        previous={**self.item,'preparation':{'stage':'saved'}}
        raw={'id':'movie','title':'隔离验证影片','sources':[]}
        clean=server.clean_item(raw,previous)
        self.assertIn('sources/movie.mkv',clean['files']);self.assertIn(f'media/{self.asset}.mp4',clean['files'])

    def test_authenticated_tv_management_rejects_writes_and_resurrection(self):
        tv=ThreadingHTTPServer(('127.0.0.1',0),tv_server.Handler)
        tv.config={'host':'127.0.0.1','tv_host':'127.0.0.1','port':tv.server_port,'token':'fixture-pair'}
        editor=ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        for instance in (tv,editor):threading.Thread(target=instance.serve_forever,daemon=True).start()
        http=urllib.request.build_opener(urllib.request.ProxyHandler({}))
        def request(instance,path,body,headers):
            req=urllib.request.Request(f'http://127.0.0.1:{instance.server_port}'+path,data=json.dumps(body).encode(),headers=headers)
            try:
                with http.open(req) as r:return r.status,json.load(r)
            except urllib.error.HTTPError as e:
                with e:return e.code,json.load(e)
        try:
            body={'id':'movie','revision':1};auth={'X-Watchroom-Pair':'fixture-pair'}
            self.assertEqual(request(tv,'/manage/delete-preview',body,{})[0],403)
            self.assertEqual(request(tv,'/manage/delete-preview',body,auth|{'Origin':'http://evil.test'})[0],403)
            self.assertEqual(request(tv,'/api/item',body,auth)[0],404)
            self.assertEqual(request(tv,'/manage/delete',body|{'files':['sources/another.mkv']},auth)[0],400)
            code,p=request(tv,'/manage/delete-preview',body,auth);self.assertEqual(code,200)
            self.assertEqual(request(tv,'/manage/delete',body|{'confirmation':p['confirmation']},auth)[0],200)
            stale={'revision':server.read_state()['revision'],'item':{'id':'movie','title':'迟到发布','sources':[]}}
            self.assertEqual(request(editor,'/api/item',stale,{'X-Watchroom-Token':server.TOKEN})[0],409)
            self.assertEqual(server.read_state()['items'],[])
        finally:
            for instance in (tv,editor):instance.shutdown();instance.server_close()


if __name__=='__main__':unittest.main()
