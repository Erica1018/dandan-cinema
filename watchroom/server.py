"""Local watchlist. No scraping, downloads, credentials or platform login."""
import argparse
import hmac
import json
import os
import secrets
import threading
import time
import uuid
import resources
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent
from settings import DATA
STATE = Path(os.environ.get('WATCHROOM_STATE', str(DATA / 'library.json')))
LOCK = threading.RLock()
TOKEN = secrets.token_urlsafe(32)
STATUSES = {'want', 'watching', 'watched', 'inbox'}
KINDS = {'platform', 'cloud', 'direct', 'local'}

def seed():
    return {'revision': 1, 'items': []}


def read_state():
    if STATE.exists():
        return json.loads(STATE.read_text(encoding='utf-8'))
    return seed()

def save_state(state):
    STATE.parent.mkdir(parents=True, exist_ok=True)
    temp = STATE.with_suffix('.tmp')
    temp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(temp, STATE)

def clean_text(value, maximum=1000):
    if not isinstance(value, str) or len(value) > maximum:
        raise ValueError('Invalid text or text too long / 内容格式不正确或文字太长')
    return value.strip()

def clean_douban(value):
    if not isinstance(value, dict):
        raise ValueError('Invalid Douban metadata / 豆瓣资料格式不正确')
    if not value:
        return {}
    import re
    import datetime
    url = clean_text(value.get('url', ''), 200)
    if not re.fullmatch(r'https://movie\.douban\.com/subject/[0-9]+/', url):
        raise ValueError('Use a Douban title-detail URL / 请使用豆瓣作品详情页链接')
    rating = value.get('rating')
    if rating is not None and (type(rating) not in (int, float) or not 0 < rating <= 10):
        raise ValueError('Douban rating must be greater than 0 and at most 10; use null when absent / 豆瓣评分应为0到10之间的分数；暂无评分留空')
    checked = clean_text(value.get('checked_on', ''), 10)
    try:
        datetime.date.fromisoformat(checked)
    except ValueError:
        raise ValueError('Record the rating verification date / 请记录豆瓣评分的核验日期') from None
    return {'url': url, 'rating': rating, 'checked_on': checked}

def clean_artwork(value):
    if not isinstance(value, dict):
        raise ValueError('Invalid artwork metadata / 封面格式不正确')
    if not value:
        return {}
    import re
    result = {}
    for key in ('hero', 'thumbnail'):
        asset = clean_text(value.get(key, ''), 64)
        if not re.fullmatch(r'[0-9a-f]{64}', asset):
            raise ValueError('Invalid artwork ID / 无效封面编号')
        image = STATE.parent / 'artwork' / (asset + '.jpg')
        if not image.is_file() or image.stat().st_size > 1000000:
            raise ValueError('Artwork missing or too large / 封面尚未准备完成或文件太大')
        result[key] = asset
    result['credit'] = clean_text(value.get('credit', ''), 64)
    return result

def clean_item(raw, previous=None):
    if not isinstance(raw, dict):
        raise ValueError('Invalid item / 内容格式不正确')
    title = clean_text(raw.get('title', ''), 180)
    if not title:
        raise ValueError('Enter a title; a link is optional / 请填一个名称，暂时没有链接也可以')
    status = raw.get('status', 'want')
    if status not in STATUSES:
        raise ValueError('Unknown viewing status / 未知观看状态')
    sources = raw.get('sources', [])
    if not isinstance(sources, list) or len(sources) > 12:
        raise ValueError('At most 12 sources per item / 每条内容最多保存 12 个入口')
    result_sources = []
    prior_sources = list((previous or {}).get("sources", []))
    used_source_ids = set()
    for source in sources:
        kind = source.get('kind', 'platform')
        if kind not in KINDS:
            raise ValueError('Unknown source type / 未知来源类型')
        url = clean_text(source.get('url', ''), 4096)
        if not url:
            continue
        if kind != 'local':
            parsed = urlsplit(url)
            if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password:
                raise ValueError('Use a complete HTTP or HTTPS URL without embedded credentials / 请使用完整的 http:// 或 https:// 链接')
        label = clean_text(source.get('label', ''), 100) or '观看入口'
        prior = next((s for s in prior_sources if s.get('id') not in used_source_ids and
                      (s.get('id') == source.get('id') or (s.get('kind'), s.get('label'), s.get('url')) == (kind, label, url))), None)
        source_id = prior['id'] if prior else uuid.uuid4().hex[:12]
        used_source_ids.add(source_id)
        result_sources.append({'id': source_id, 'kind': kind, 'label': label, 'url': url})
    tags = raw.get('tags', [])
    if not isinstance(tags, list) or len(tags) > 10:
        raise ValueError('Invalid tags / 标签格式不正确')
    preparation = raw.get('preparation', (previous or {}).get('preparation', {}))
    if not isinstance(preparation, dict):
        raise ValueError('Invalid preparation metadata / 准备状态格式不正确')
    stage = preparation.get('stage', 'saved')
    if stage not in ('saved', 'searching', 'needs_access', 'preparing', 'ready', 'unavailable'):
        raise ValueError('Unknown preparation state / 未知准备状态')
    playback = raw.get('playback', (previous or {}).get('playback', {}))
    if not isinstance(playback, dict):
        raise ValueError('Invalid playback metadata / 播放信息格式不正确')
    asset = clean_text(playback.get('asset', ''), 64)
    if asset:
        import re
        if not re.fullmatch(r'[0-9a-f]{64}', asset):
            raise ValueError('Invalid media ID / 无效媒体编号')
        if not (STATE.parent / 'media' / (asset + '.mp4')).is_file():
            raise ValueError('Prepared media file not found / 媒体文件尚未准备完成')
    checks = preparation.get('checks', {})
    if not isinstance(checks, dict):
        raise ValueError('Invalid verification metadata / 核验信息格式不正确')
    checks = {k: checks.get(k) is True for k in ('identity', 'complete', 'original_audio', 'bilingual')}
    evidence = clean_text(preparation.get('evidence', ''), 3000)
    if previous and title != previous.get('title') and stage == 'ready':
        stage = 'saved'
        checks = {key: False for key in checks}
        evidence = ''
    if stage == 'ready' and (not asset or not (checks['identity'] and checks['complete']) or not evidence):
        raise ValueError('Verify media identity and completeness, and record evidence before publishing.')
    same_title = not previous or title == previous.get('title')
    metadata_previous = previous if previous and same_title else {}
    synopsis = clean_text(raw.get('synopsis', metadata_previous.get('synopsis', '')), 600) if same_title else ''
    douban = clean_douban(raw.get('douban', metadata_previous.get('douban', {}))) if same_title else {}
    artwork = clean_artwork(raw.get('artwork', metadata_previous.get('artwork', {}))) if same_title else {}
    file_item = {'files': list((previous or {}).get('files', [])) + resources.clean_files(STATE.parent, raw.get('files', [])), 'playback': {'asset': asset}, 'artwork': artwork}
    files = resources.linked_files(STATE.parent, file_item)
    return {'id': previous['id'] if previous else uuid.uuid4().hex[:12], 'title': title,
            'category': clean_text(raw.get('category', '视频'), 30), 'status': status,
            'note': clean_text(raw.get('note', ''), 3000), 'tags': [clean_text(x, 40) for x in tags if x],
            'sources': result_sources, 'art': previous.get('art', 'green') if previous else 'green',
            'provenance': previous.get('provenance', '你手动添加') if previous else '你手动添加',
            'created': previous.get('created', int(time.time())) if previous else int(time.time()),
            'preparation': {'stage': stage, 'message': clean_text(preparation.get('message', ''), 500),
                            'checks': checks, 'evidence': evidence},
            'playback': {'asset': asset}, 'synopsis': synopsis, 'douban': douban, 'artwork': artwork, 'files': files}

def manage_delete(path, data, state=None):
    with LOCK:
        state = read_state() if state is None else state
        item = next((x for x in state['items'] if x['id'] == data.get('id')), None)
        if item is None:
            receipt = state.get('deleted', {}).get(data.get('id'))
            return (200, receipt | {'revision': state['revision']}) if receipt else (404, {'error': 'Item no longer exists / 影片已不存在'})
        if data.get('revision') != state['revision']:
            return 409, {'error': 'Library changed; get a fresh deletion preview. / 片单已有更新，请重新点管理后删除。'}
        try:
            value = resources.plan(STATE.parent, state, item)
            if path.endswith('delete-preview'):
                return 200, resources.public_plan(value, state['revision'])
            return resources.delete(STATE.parent, state, item, data.get('confirmation'), save_state)
        except (ValueError, OSError):
            return 409, {'error': 'Item is busy or its registered files need checking. Retry deletion later. / 影片正在准备、播放或文件关联待核实，请稍后重试删除。'}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Media links can contain access tokens. Never log request contents.

    def allowed_host(self):
        return self.headers.get('Host') in (f'127.0.0.1:{self.server.server_port}', f'localhost:{self.server.server_port}')

    def reply(self, status, data, content_type='application/json; charset=utf-8'):
        payload = data if isinstance(data, bytes) else json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(payload)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; media-src http: https: blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        if not self.allowed_host():
            return self.reply(403, {'error': 'Local access only / 仅支持本机访问'})
        path = urlsplit(self.path).path
        if path == '/api/state':
            with LOCK:
                return self.reply(200, {**read_state(), 'csrf': TOKEN})
        if path == '/api/tv':
            return self.reply(200, {'reachable': False, 'message': 'TV status is shown on your TV / 请在电视查看连接状态'})
        if path == '/api/health':
            return self.reply(200, {'name': 'Dandan Cinema', 'version': '0.1.0-preview.1', 'api': 1})
        assets = {'/': ('index.html', 'text/html; charset=utf-8'), '/app.js': ('app.js', 'text/javascript; charset=utf-8'),
                  '/style.css': ('style.css', 'text/css; charset=utf-8'), '/i18n.js': ('i18n.js', 'text/javascript; charset=utf-8'),
                  '/brand/logo.png': ('brand/dandan-cinema-logo.png', 'image/png')}
        if path in assets:
            filename, typ = assets[path]
            return self.reply(200, (ROOT / filename).read_bytes(), typ)
        return self.reply(404, {'error': 'Page not found / 页面不存在'})

    def do_POST(self):
        origin = self.headers.get('Origin')
        good_origins = (f'http://127.0.0.1:{self.server.server_port}', f'http://localhost:{self.server.server_port}')
        if (not self.allowed_host() or (origin and origin not in good_origins)
                or not hmac.compare_digest(self.headers.get('X-Watchroom-Token', ''), TOKEN)):
            return self.reply(403, {'error': 'Use the local library page or authorized adapter / 请从片单页面操作'})
        try:
            size = int(self.headers.get('Content-Length', 0))
            if size < 1 or size > 200_000:
                raise ValueError('Invalid request size / 请求大小不正确')
            data = json.loads(self.rfile.read(size))
            if not isinstance(data, dict):
                raise ValueError('Expected a JSON object')
            path = urlsplit(self.path).path
            with LOCK:
                state = read_state()
                if path == '/api/item':
                    if data.get('revision') != state['revision']:
                        return self.reply(409, {'error': 'Library changed; refresh and retry / 片单刚刚有更新，请刷新后再试'})
                    raw = data.get('item', {})
                    previous = next((x for x in state['items'] if x['id'] == raw.get('id')), None)
                    if raw.get('id') and previous is None:
                        return self.reply(409, {'error': 'Item was deleted and cannot be republished. / 这部影片已删除，不能重新发布。'})
                    if previous and previous.get('deletion'):
                        return self.reply(409, {'error': 'Cleanup is pending; retry deletion. / 这部影片正在清理，请重试删除。'})
                    item = clean_item(raw, previous)
                    state['items'] = [item if x['id'] == item['id'] else x for x in state['items']]
                    if previous is None:
                        state['items'].insert(0, item)
                    state['revision'] += 1
                    save_state(state)
                    return self.reply(200, {'item': item})
                if path in ('/api/delete-preview', '/api/delete'):
                    code, result = manage_delete(path, data, state)
                    return self.reply(code, result)
                return self.reply(404, {'error': 'Unknown operation / 操作不存在'})
        except (ValueError, TypeError, KeyError, AttributeError) as exc:
            return self.reply(400, {'error': str(exc)})
        except Exception:
            return self.reply(502, {'error': 'Operation incomplete; check the service and retry / 本次操作未完成；如为电视播放，请检查电视联网和视频链接是否可直接访问'})

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=18776)
    args = parser.parse_args()
    with LOCK:
        if not STATE.exists():
            save_state(seed())
    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
