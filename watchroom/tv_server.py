"""Paired TV read-only library endpoint. Separate from the local editor."""
import argparse
import hmac
import json
import re
import server as editor
import resources
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

PROJECT = Path(__file__).resolve().parent.parent
STATE = editor.STATE
MEDIA = STATE.parent / 'media'

def library():
    state = json.loads(STATE.read_text(encoding='utf-8'))
    items = []
    for item in state['items']:
        visible = {key: item.get(key, '') for key in ('id', 'title', 'category', 'status', 'note', 'art', 'tags')}
        artwork = item.get('artwork', {})
        if isinstance(artwork, dict) and all(re.fullmatch(r'[0-9a-f]{64}', str(artwork.get(k, ''))) for k in ('hero', 'thumbnail')):
            visible['artwork'] = {k: '/art/' + artwork[k] + '.jpg' for k in ('hero', 'thumbnail')}
            visible['artwork']['credit'] = str(artwork.get('credit', ''))[:64]
        visible['synopsis'] = item.get('synopsis', '')
        visible['douban'] = {k: item.get('douban', {}).get(k) for k in ('url', 'rating', 'checked_on')} if item.get('douban') else {}
        visible['sources'] = [dict(source, url='' if source['kind'] == 'local' else source['url']) for source in item['sources']]
        prep = item.get('preparation', {})
        visible['preparation'] = {k: prep.get(k, '') for k in ('stage', 'message')}
        if item.get('deletion'):
            visible['preparation'] = {'stage': 'unavailable', 'message': 'Cleanup pending. Retry deletion from Manage. / 本机文件尚未清理完，请在管理中重试删除。'}
        asset = item.get('playback', {}).get('asset', '')
        if not item.get('deletion') and prep.get('stage') == 'ready' and re.fullmatch(r'[0-9a-f]{64}', asset) and (MEDIA / (asset + '.mp4')).is_file():
            visible['playback'] = {'path': '/media/' + asset + '.mp4',
                                   'subtitles': '/media/' + asset + '.json' if (MEDIA / (asset + '.json')).is_file() else ''}
        items.append(visible)
    return {'items': items, 'revision': state['revision']}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, payload):
        raw = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(raw)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(raw)

    def authorized(self):
        config = self.server.config
        return not (self.client_address[0] not in (config['tv_host'], config['host'], '127.0.0.1')
            or self.headers.get('Host') != config['host'] + ':' + str(config['port'])
            or not hmac.compare_digest(self.headers.get('X-Watchroom-Pair', ''), config['token']))

    def media(self, path, head=False):
        name = path.removeprefix('/media/')
        if not re.fullmatch(r'(?:[0-9a-f]{64}|test-stream)\.(?:mp4|json)', name):
            return self.reply(404, {'error': 'Not found'})
        file = (MEDIA / name).resolve()
        if file.parent != MEDIA.resolve() or not file.is_file():
            return self.reply(404, {'error': 'Not found'})
        size = file.stat().st_size
        start, end, code = 0, size - 1, 200
        requested = self.headers.get('Range')
        if requested:
            match = re.fullmatch(r'bytes=(\d*)-(\d*)', requested)
            try:
                if not match or not any(match.groups()):
                    raise ValueError()
                left, right = match.groups()
                if left:
                    start = int(left)
                    end = min(int(right), size - 1) if right else size - 1
                else:
                    count = int(right)
                    if count <= 0:
                        raise ValueError()
                    start = max(0, size - count)
                if start > end or start >= size:
                    raise ValueError()
                code = 206
            except ValueError:
                self.send_response(416)
                self.send_header('Content-Range', 'bytes */' + str(size))
                self.send_header('Content-Length', '0')
                self.end_headers()
                return
        self.send_response(code)
        self.send_header('Content-Type', 'video/mp4' if name.endswith('.mp4') else 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(end - start + 1))
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Cache-Control', 'private, no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        if code == 206:
            self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
        self.end_headers()
        if head:
            return
        try:
            with resources.open_stream(file, editor.LOCK) as stream:
                stream.seek(start)
                remaining = end - start + 1
                while remaining:
                    block = stream.read(min(262144, remaining))
                    if not block:
                        break
                    self.wfile.write(block)
                    remaining -= len(block)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass  # Normal when TV seeks, pauses or exits. Never log private URLs.

    def artwork(self, path, head=False):
        match = re.fullmatch(r'/art/([0-9a-f]{64})\.jpg', path)
        if not match:
            return self.reply(404, {'error': 'Not found'})
        folder = STATE.parent / 'artwork'
        file = folder / (match.group(1) + '.jpg')
        if not file.is_file() or file.stat().st_size > 1000000:
            return self.reply(404, {'error': 'Not found'})
        raw = file.read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', 'image/jpeg')
        self.send_header('Content-Length', str(len(raw)))
        self.send_header('Cache-Control', 'private, max-age=31536000, immutable')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        if not head:
            try:
                self.wfile.write(raw)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def do_HEAD(self):
        if not self.authorized():
            return self.reply(403, {'error': 'Not paired'})
        if self.path.startswith('/art/'):
            return self.artwork(urlsplit(self.path).path, head=True)
        if self.path.startswith('/media/'):
            return self.media(urlsplit(self.path).path, head=True)
        if self.path.startswith('/online/'):
            return self.online(head=True)
        self.send_response(404)
        self.send_header('Content-Length', '0')
        self.end_headers()

    def do_GET(self):
        if not self.authorized():
            return self.reply(403, {'error': 'Not paired'})
        if self.path.startswith('/art/'):
            return self.artwork(urlsplit(self.path).path)
        if self.path.startswith('/media/'):
            return self.media(urlsplit(self.path).path)
        if self.path.startswith('/online/'):
            return self.online()
        if self.path != '/library':
            return self.reply(404, {'error': 'Not found'})
        try:
            self.reply(200, library())
        except (OSError, ValueError, KeyError):
            self.reply(503, {'error': 'Library temporarily unavailable'})

    def do_POST(self):
        if not self.authorized() or self.headers.get('Origin'):
            return self.reply(403, {'error': 'Not paired'})
        if self.path not in ('/manage/delete-preview', '/manage/delete'):
            return self.reply(404, {'error': 'Not found'})
        try:
            size = int(self.headers.get('Content-Length', 0))
            if not 0 < size <= 4096:
                raise ValueError()
            data = json.loads(self.rfile.read(size))
            if not isinstance(data, dict) or set(data) - {'id', 'revision', 'confirmation'}:
                raise ValueError()
            code, result = editor.manage_delete('/api/' + self.path.rsplit('/', 1)[1], data)
            return self.reply(code, result)
        except (ValueError, TypeError):
            return self.reply(400, {'error': 'Invalid management request'})
        except OSError:
            return self.reply(503, {'error': 'The computer could not complete deletion. Retry. / 电脑暂时无法完成删除，请重试。'})

    def online(self, head=False):
        return self.reply(404, {'error': 'Provider adapter not installed'})

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding='utf-8-sig'))
    server = ThreadingHTTPServer((config['host'], config['port']), Handler)
    server.config = config
    server.serve_forever()
