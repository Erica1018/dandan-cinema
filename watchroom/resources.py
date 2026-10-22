"""Owned movie files: stable item IDs, confined paths, one coupled delete."""
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import time

ACTIVE = {}


def checked(base, name):
    if not isinstance(name, str) or len(name) > 300 or '\\' in name or ':' in name:
        raise ValueError('Invalid managed-file reference / 影片文件关联无效')
    parts = PurePosixPath(name).parts
    if not parts or any(p in ('', '.', '..') for p in name.split('/')):
        raise ValueError('Invalid managed-file reference / 影片文件关联无效')
    valid = (re.fullmatch(r'media/[0-9a-f]{64}\.(?:mp4|json|files\.json)', name)
             or re.fullmatch(r'artwork/[0-9a-f]{64}\.jpg', name)
             or re.fullmatch(r'sources/[^/]+\.(?:mkv|mp4|webm|mov|m4a|srt|vtt|ass)', name, re.I)
             or re.fullmatch(r'files/[a-zA-Z0-9_-]{1,64}/[^/]+\.(?:mkv|mp4|webm|mov|m4a|srt|vtt|ass|jpg|png)', name, re.I)
             or re.fullmatch(r'[a-z0-9-]+/[^/]+\.(?:srt|vtt|ass)', name))
    if not valid:
        raise ValueError('File is outside the managed media scope / 影片文件不在影院管理范围内')
    root = Path(base).resolve()
    current = Path(base)
    for part in parts:
        current = current / part
        if current.exists() or current.is_symlink():
            info = current.lstat()
            if stat.S_ISLNK(info.st_mode) or getattr(info, 'st_file_attributes', 0) & 0x400:
                raise ValueError('Managed files must not traverse junctions or symbolic links / 影片文件关联不能经过目录联接或符号链接')
    target = current.resolve()
    if not target.is_relative_to(root) or target == root:
        raise ValueError('File escapes the cinema directory / 影片文件关联越出影院目录')
    if target.exists() and not target.is_file():
        raise ValueError('Managed reference must be a file / 影片关联必须是文件')
    return target


def clean_files(base, values):
    if not isinstance(values, list) or len(values) > 300:
        raise ValueError('Invalid managed-file list / 影片文件关联格式不正确')
    result = []
    for name in values:
        checked(base, name)
        if name not in result:
            result.append(name)
    return result


def asset_files(base, asset):
    if not re.fullmatch(r'[0-9a-f]{64}', str(asset)):
        return []
    names = [f'media/{asset}.mp4', f'media/{asset}.json']
    manifest = checked(base, f'media/{asset}.files.json')
    if manifest.is_file():
        data = json.loads(manifest.read_text(encoding='utf-8'))
        if data.get('asset') != asset:
            raise ValueError('Media registry ID mismatch / 媒体文件关联编号不一致')
        names += clean_files(base, data.get('files', [])) + [f'media/{asset}.files.json']
    return names


def linked_files(base, item):
    names = list(item.get('files', []))
    names += asset_files(base, item.get('playback', {}).get('asset', ''))
    for key in ('hero', 'thumbnail'):
        image = item.get('artwork', {}).get(key, '')
        if re.fullmatch(r'[0-9a-f]{64}', str(image)):
            names.append(f'artwork/{image}.jpg')
    return clean_files(base, names)


def fingerprint(path):
    if not path.exists():
        return None
    info = path.stat()
    return [info.st_size, info.st_mtime_ns, info.st_ino]


def plan(base, state, item):
    if item.get('preparation', {}).get('stage') == 'preparing':
        raise ValueError('Media is being prepared. Wait before deleting. / 这部影片正在下载或转换，请准备结束后再删除。')
    shared = set()
    for other in state['items']:
        if other['id'] != item['id']:
            shared.update(str(checked(base, n)).casefold() for n in linked_files(base, other))
    files, kept = [], 0
    for name in linked_files(base, item):
        path = checked(base, name)
        if str(path).casefold() in shared:
            kept += int(path.exists())
            continue
        if ACTIVE.get(str(path).casefold(), 0):
            raise ValueError('Media is playing. Exit playback before deleting. / 这部影片正在播放，请退出播放后再删除。')
        signature = fingerprint(path)
        files.append({'path': name, 'signature': signature})
    payload = {'id': item['id'], 'files': files, 'shared_files': kept}
    proof = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()
    return {**payload, 'confirmation': proof,
            'file_count': sum(f['signature'] is not None for f in files),
            'bytes': sum(f['signature'][0] for f in files if f['signature'])}


def public_plan(value, revision):
    return {k: value[k] for k in ('id', 'confirmation', 'file_count', 'bytes', 'shared_files')} | {'revision': revision}


def delete(base, state, item, confirmation, save):
    value = plan(base, state, item)
    if not confirmation or confirmation != value['confirmation']:
        return 409, {'error': 'Registered files changed. Get a new deletion preview. / 影片关联文件已有变化，请重新点管理后删除。'}
    # Persist before the first unlink. A crash or a locked file leaves a retryable
    # card, and never turns a partial cleanup into a successful card deletion.
    pending = item.get('deletion', {})
    removed_bytes = int(pending.get('bytes', 0))
    item['files'] = linked_files(base, item)
    item['deletion'] = {'status': 'pending', 'bytes': removed_bytes}
    state['revision'] += 1
    save(state)
    try:
        for entry in value['files']:
            path = checked(base, entry['path'])
            if fingerprint(path) != entry['signature']:
                raise ValueError('Files changed. Retry with a new preview. / 影片文件已有变化，请重新点删除。')
            if entry['signature'] is not None:
                path.unlink()
                removed_bytes += entry['signature'][0]
                item['deletion']['bytes'] = removed_bytes
                save(state)
    except (OSError, ValueError):
        item['deletion']['status'] = 'retry'
        state['revision'] += 1
        save(state)
        return 409, {'error': 'Partial cleanup; the card is retained. Exit playback and retry deletion. / 部分本机文件尚未清理，卡片已保留。请退出播放后再点删除重试。',
                     'partial': True, 'bytes': removed_bytes, 'revision': state['revision']}
    receipt = {'id': item['id'], 'deleted': True, 'bytes': removed_bytes,
               'shared_files': value['shared_files'], 'at': int(time.time())}
    state.setdefault('deleted', {})[item['id']] = receipt
    state['items'] = [x for x in state['items'] if x['id'] != item['id']]
    state['revision'] += 1
    save(state)
    return 200, receipt | {'revision': state['revision']}


@contextmanager
def open_stream(file, lock):
    key = str(Path(file).resolve()).casefold()
    with lock:
        stream = Path(file).open('rb')
        ACTIVE[key] = ACTIVE.get(key, 0) + 1
    try:
        yield stream
    finally:
        stream.close()
        with lock:
            ACTIVE[key] -= 1
            if not ACTIVE[key]:
                del ACTIVE[key]
