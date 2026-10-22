import json,sys,tempfile,threading,unittest,urllib.request,urllib.error
from pathlib import Path
from http.server import ThreadingHTTPServer
import server
from client import Client,APIError

class API(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.old=server.STATE
        server.STATE=Path(self.tmp.name)/'library.json';server.save_state(server.seed())
        self.service=ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        threading.Thread(target=self.service.serve_forever,daemon=True).start()
        self.base=f'http://127.0.0.1:{self.service.server_port}';self.client=Client(self.base)
    def tearDown(self):
        self.service.shutdown();self.service.server_close();server.STATE=self.old;self.tmp.cleanup()
    def test_add_deduplicate_update_and_confirmed_delete(self):
        item=self.client.add('Sample film');same=self.client.add('sample FILM');self.assertEqual(item['id'],same['id'])
        item=self.client.source(item['id'],'https://example.org/authorized.mp4?access=private-example')
        self.assertNotIn('private-example',json.dumps(item));self.assertEqual(len(item['sources']),1)
        self.assertEqual(self.client.modify(item['id'],{'status':'watching'})['status'],'watching')
        preview=self.client.preview_delete(item['id']);self.assertEqual(preview['file_count'],0)
        self.client.add('Another film')
        with self.assertRaises(APIError) as error:self.client.delete(item['id'],preview['revision'],preview['confirmation'])
        self.assertEqual(error.exception.status,409)
        preview=self.client.preview_delete(item['id']);self.assertTrue(self.client.delete(item['id'],preview['revision'],preview['confirmation'])['deleted'])
        self.assertEqual(len(self.client.list()),1)
    def test_csrf_origin_and_malformed_input(self):
        state=self.client.state()
        for headers,body,expected in [({}, {},403),({'X-Watchroom-Token':state['csrf'],'Origin':'https://example.org'}, {},403),({'X-Watchroom-Token':state['csrf']}, [],400),({'X-Watchroom-Token':state['csrf']},{'revision':state['revision'],'item':{'title':'bad','sources':[None]}},400)]:
            request=urllib.request.Request(self.base+'/api/item',data=json.dumps(body).encode(),headers=headers)
            with self.assertRaises(urllib.error.HTTPError) as error:self.client.http.open(request)
            self.assertEqual(error.exception.code,expected);error.exception.close()
        self.assertEqual(self.client.list(),[])
    def test_local_only_client(self):
        for url in ('https://example.org','http://192.168.1.10:18776','http://user:pass@localhost','http://localhost/path'):
            with self.assertRaises(ValueError):Client(url)

if __name__=='__main__':unittest.main()
