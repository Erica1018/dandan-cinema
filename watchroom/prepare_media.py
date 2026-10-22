"""Prepare an explicitly selected local/officially cached movie for the paired TV.
Does not download sources, infer rights or mark a movie verified.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from settings import DATA
MEDIA = DATA / 'media'

def cues(path):
    raw = path.read_text(encoding='utf-8-sig').replace('\r', '')
    pattern = r'(\d\d):(\d\d):(\d\d)[,.](\d{3})\s*-->\s*(\d\d):(\d\d):(\d\d)[,.](\d{3})[^\n]*\n(.*?)(?=\n\s*\n|\Z)'
    result = []
    for match in re.finditer(pattern, raw, re.S):
        values = list(map(int, match.groups()[:8]))
        def ms(v): return ((v[0]*60+v[1])*60+v[2])*1000+v[3]
        start, end = ms(values[:4]), ms(values[4:])
        text = re.sub(r'<[^>]*>', '', match.group(9)).strip()
        if start < end and text:
            result.append({'start': start, 'end': end, 'text': text[:1500]})
    if not result:
        raise ValueError('No supported SRT cues found; use UTF-8 SRT')
    if len(result) > 20000:
        raise ValueError('Subtitle cue limit exceeded')
    return sorted(result, key=lambda x: x['start'])

def main():
    p = argparse.ArgumentParser()
    p.add_argument('input', type=Path)
    p.add_argument('--ffmpeg', type=Path, required=True)
    p.add_argument('--zh', type=Path)
    p.add_argument('--en', type=Path)
    p.add_argument('--audio', type=int, default=0, help='audio stream index, verify original language before choosing')
    p.add_argument('--burn-subtitle', type=int, help='explicit bitmap subtitle stream index, after visual verification')
    p.add_argument('--subtitle-canvas', default='1920x1080', help='verified bitmap subtitle canvas')
    p.add_argument('--qsv', action='store_true', help='use the locally verified Intel video conversion path')
    p.add_argument('--progress', type=Path, help='optional ffmpeg progress file, contains no credentials')
    a = p.parse_args()
    source = a.input.resolve(strict=True)
    MEDIA.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(MEDIA).free < max(source.stat().st_size*2, 5*1024**3):
        raise RuntimeError('Insufficient cache space; preserve at least 5 GB free')
    probe = a.ffmpeg.with_name('ffprobe.exe' if a.ffmpeg.suffix else 'ffprobe')
    info = json.loads(subprocess.check_output([str(probe), '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(source)]))
    video = next(s for s in info['streams'] if s['codec_type'] == 'video')
    audios = [s for s in info['streams'] if s['codec_type'] == 'audio']
    audio = audios[a.audio]
    if a.audio < 0:
        raise ValueError('Audio index must be nonnegative')
    embedded = None
    if a.burn_subtitle is not None:
        tracks = [s for s in info['streams'] if s['codec_type'] == 'subtitle']
        if a.burn_subtitle < 0 or a.burn_subtitle >= len(tracks):
            raise ValueError('Unknown subtitle stream')
        embedded = tracks[a.burn_subtitle]
        if embedded['codec_name'] not in ('hdmv_pgs_subtitle', 'dvd_subtitle', 'dvb_subtitle'):
            raise ValueError('Use UTF-8 SRT for text subtitles')
        if a.zh or a.en or not re.fullmatch(r'[1-9]\d{1,3}x[1-9]\d{1,3}', a.subtitle_canvas):
            raise ValueError('Choose either bitmap subtitles or external SRT, with a valid canvas')
    hdr = video.get('color_transfer') in ('smpte2084', 'arib-std-b67')
    subtitles = {'zh': cues(a.zh) if a.zh else [], 'en': cues(a.en) if a.en else []}
    digest = hashlib.sha256()
    with source.open('rb') as stream:
        for block in iter(lambda: stream.read(1024*1024), b''):
            digest.update(block)
    digest.update(json.dumps(subtitles, ensure_ascii=False, sort_keys=True).encode())
    digest.update(json.dumps({'profile':'tv-v3-sdr-3mbps','audio':a.audio,'bitmap':a.burn_subtitle,
                             'canvas':a.subtitle_canvas,'qsv':a.qsv},sort_keys=True).encode())
    asset = digest.hexdigest()
    target = MEDIA / (asset + '.mp4')
    if not target.exists():
        with tempfile.TemporaryDirectory(prefix='prepare-', dir=MEDIA) as directory:
            tmp = Path(directory) / 'video.mp4'
            from fractions import Fraction
            copy_video = not hdr and embedded is None and video['codec_name']=='h264' and video.get('pix_fmt')=='yuv420p' and video['width']<=1920 and video['height']<=1080 and 0<int(video.get('bit_rate',0))<=2500000 and float(Fraction(video.get('avg_frame_rate','60/1')))<=30
            input_args = []
            if embedded is not None:
                input_args += ['-canvas_size', a.subtitle_canvas]
            mapping = ['-map', '0:v:0']
            if copy_video:
                video_args = ['-c:v', 'copy']
            else:
                scale = min(1920/video['width'], 1080/video['height'], 1)
                width, height = int(video['width']*scale)//2*2, int(video['height']*scale)//2*2
                if a.qsv:
                    decoder = {'hevc':'hevc_qsv','h264':'h264_qsv'}.get(video['codec_name'])
                    if not decoder:
                        raise ValueError('QSV path currently supports H264/HEVC inputs; use software otherwise')
                    input_args += ['-hwaccel','qsv','-hwaccel_output_format','qsv','-c:v:0',decoder]
                    vf = f'vpp_qsv=w={width}:h={height}:format=nv12'
                    if hdr:
                        vf += ':tonemap=1:out_color_matrix=bt709:out_color_primaries=bt709:out_color_transfer=bt709'
                    vf += ',hwdownload,format=nv12'
                    encoder = ['-c:v','h264_qsv','-preset','veryfast','-b:v','2500k']
                else:
                    if hdr:
                        vf = f'zscale=w={width}:h={height}:t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p'
                    else:
                        vf = f'scale={width}:{height}'
                    encoder = ['-c:v','libx264','-preset','veryfast','-crf','22']
                vf += ',pad=1920:1080:(ow-iw)/2:(oh-ih)/2'
                if float(Fraction(video.get('avg_frame_rate','60/1'))) > 30:
                    vf += ',fps=30'
                # Remove inherited HDR signalling after the pixels have become SDR.
                clean = 'sidedata=mode=delete:type=MASTERING_DISPLAY_METADATA,sidedata=mode=delete:type=CONTENT_LIGHT_LEVEL'
                if embedded is not None:
                    graph = f'[0:v:0]{vf}[base];[0:s:{a.burn_subtitle}]scale=1920:1080[sub];[base][sub]overlay=eof_action=pass:repeatlast=0,{clean},format=nv12[out]'
                else:
                    graph = f'[0:v:0]{vf},{clean},format=nv12[out]'
                mapping = ['-filter_complex',graph,'-map','[out]']
                video_args = [*encoder,'-maxrate','3M','-bufsize','6M','-pix_fmt','nv12' if a.qsv else 'yuv420p']
                if hdr:
                    video_args += ['-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709']
            progress_args = ['-progress',str(a.progress.resolve()),'-stats_period','5'] if a.progress else []
            subprocess.run([str(a.ffmpeg), '-hide_banner', '-loglevel', 'error', '-nostdin', *input_args,
                '-i', str(source), *mapping, '-map', f'0:a:{a.audio}', *video_args,
                '-c:a', 'aac', '-b:a', '192k', '-ac', '2', '-movflags', '+faststart', '-sn',
                '-map_metadata','-1','-map_chapters','-1',*progress_args,str(tmp)], check=True)
            output = json.loads(subprocess.check_output([str(probe),'-v','error','-show_format','-of','json',str(tmp)]))
            if abs(float(output['format']['duration'])-float(info['format']['duration'])) > 1:
                raise RuntimeError('Output duration does not match source; not publishing incomplete media')
            tmp.replace(target)
    subpath = MEDIA / (asset + '.json')
    subpath.write_text(json.dumps(subtitles, ensure_ascii=False), encoding='utf-8')
    # Persist source ownership alongside the asset so every publisher can recover
    # the same links. External originals are not silently enrolled for deletion.
    import resources
    owned = [f'media/{asset}.mp4', f'media/{asset}.json']
    for original in (source, a.zh, a.en):
        if original is None:
            continue
        absolute = original.resolve()
        if absolute.is_relative_to(MEDIA.parent.resolve()):
            relative = absolute.relative_to(MEDIA.parent.resolve()).as_posix()
            resources.checked(MEDIA.parent, relative)
            owned.append(relative)
    registry = MEDIA / (asset + '.files.json')
    temporary = registry.with_suffix('.tmp')
    temporary.write_text(json.dumps({'asset': asset, 'files': list(dict.fromkeys(owned))}, ensure_ascii=False), encoding='utf-8')
    temporary.replace(registry)
    print(json.dumps({'managed_files': owned, 'asset': asset, 'duration_seconds': float(info['format']['duration']), 'input_audio_tags': audio.get('tags', {}),
        'bitmap_subtitle_tags':embedded.get('tags',{}) if embedded else None,'hdr_to_sdr':hdr,
        'zh_cues': len(subtitles['zh']), 'en_cues': len(subtitles['en']), 'bytes': target.stat().st_size, 'content_verified': False}, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
