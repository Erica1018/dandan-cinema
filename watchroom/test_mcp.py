"""End-to-end stdio protocol tests against an isolated real local API."""
import asyncio,importlib.util,json,os,sys,tempfile,threading,unittest
from pathlib import Path
from http.server import ThreadingHTTPServer
import server

@unittest.skipUnless(importlib.util.find_spec('mcp'),'Optional MCP dependencies not installed')
class MCP(unittest.TestCase):
    def test_readonly_and_explicit_write_protocol(self):
        from mcp import ClientSession,StdioServerParameters
        from mcp.client.stdio import stdio_client
        old=server.STATE
        with tempfile.TemporaryDirectory() as directory:
            server.STATE=Path(directory)/'library.json';server.save_state(server.seed())
            service=ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
            threading.Thread(target=service.serve_forever,daemon=True).start()
            async def exercise(write):
                env=dict(os.environ,DANDAN_URL=f'http://127.0.0.1:{service.server_port}',DANDAN_MCP_ALLOW_WRITE='1' if write else '0',PYTHONUTF8='1')
                params=StdioServerParameters(command=sys.executable,args=[str(Path(__file__).resolve().parents[1]/'mcp_server.py')],env=env)
                async with stdio_client(params) as (read,send):
                    async with ClientSession(read,send) as session:
                        await session.initialize();listed=await session.list_tools();names={x.name for x in listed.tools}
                        self.assertEqual(len(names),11 if write else 3)
                        health=await session.call_tool('cinema_status',{});self.assertFalse(health.is_error)
                        async def call(name,arguments):
                            result=await session.call_tool(name,arguments);self.assertFalse(result.is_error,result.content)
                            return json.loads(result.content[0].text)
                        if write:
                            item=await call('library_add',{'title':'MCP test film'})
                            await call('library_set_status',{'item_id':item['id'],'status':'watched'})
                            preview=await call('library_delete_preview',{'item_id':item['id']})
                            arguments={'item_id':item['id'],'revision':preview['revision'],'confirmation':preview['confirmation']}
                            denied=await session.call_tool('library_delete',arguments);self.assertTrue(denied.is_error)
                            receipt=await call('library_delete',arguments|{'user_confirmed':True});self.assertTrue(receipt['deleted'])
                        else:self.assertNotIn('library_delete',names)
            try:asyncio.run(exercise(False));asyncio.run(exercise(True))
            finally:service.shutdown();service.server_close();server.STATE=old

if __name__=='__main__':unittest.main()
