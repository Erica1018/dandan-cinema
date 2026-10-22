"""Start this instance's local editor and optional paired TV server."""
from pathlib import Path
import json
import sys
import threading
from http.server import ThreadingHTTPServer

sys.path.insert(0, str(Path(__file__).resolve().parent / 'watchroom'))
import server
import tv_server
from settings import DATA, EDITOR_PORT

def main():
    with server.LOCK:
        if not server.STATE.exists():
            server.save_state(server.seed())
    instances=[]
    started=[]
    import shutil
    media=DATA/'media';media.mkdir(parents=True,exist_ok=True)
    test_clip=media/'test-stream.mp4'
    if not test_clip.exists():shutil.copyfile(Path(__file__).parent/'watchroom-tv/res/raw/test_clip.mp4',test_clip)
    try:
        editor=ThreadingHTTPServer(('127.0.0.1', EDITOR_PORT), server.Handler)
        instances.append(editor)
        path=DATA/'tv-access.json'
        if path.exists():
            from setup_tv import validate_config
            config=validate_config(json.loads(path.read_text(encoding='utf-8')))
            tv=ThreadingHTTPServer((config['host'], config['port']), tv_server.Handler)
            tv.config=config
            instances.append(tv)
        for instance in instances:
            instance.daemon_threads=True
            threading.Thread(target=instance.serve_forever, daemon=True).start()
            started.append(instance)
        print(f'Dandan Cinema / 蛋蛋影院: http://127.0.0.1:{EDITOR_PORT}', flush=True)
        print('Press Ctrl+C to stop / 按 Ctrl+C 停止。', flush=True)
        while True:
            threading.Event().wait(30)
    except KeyboardInterrupt:
        pass
    finally:
        for instance in instances:
            if instance in started:instance.shutdown()
            instance.server_close()

if __name__=='__main__':
    main()
