"""Explicit local acceptance utility. One 3D submission per supplied round ID; no submission retry.

Uses the existing private model configuration/key. Responses and signed asset URLs stay in ignored cache.
Status polling does not create another generation task. This is not a public author/upload API.
"""
from __future__ import annotations
import argparse
import base64
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'backend/.build-cache/tokenhub-scene'
HOST = 'https://tokenhub.tencentmaas.com/v1'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def settings():
    c = json.loads((ROOT / 'backend/config/model-api.local.json').read_text(encoding='utf-8-sig'))
    if c['baseUrl'].rstrip('/') != HOST:
        raise ValueError('This utility requires the configured Guangzhou TokenHub endpoint')
    key = os.environ.get('TOKENHUB_API_KEY', '').strip() or c['apiKey'].strip()
    if not key or '\r' in key or '\n' in key:
        raise ValueError('Missing/invalid private TokenHub credential')
    return c, key


def request(path, payload=None, timeout=90):
    _, key = settings()
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(HOST + path, data=body, headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=timeout) as response:
            raw = response.read(4 * 1024 * 1024 + 1)
            if len(raw) > 4 * 1024 * 1024:
                raise ValueError('Provider response too large')
            return json.loads(raw)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'TokenHub HTTP {error.code}; response body and credentials withheld') from None
    except urllib.error.URLError:
        raise RuntimeError('TokenHub connection unavailable; no automatic retry') from None


def save(path, data):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['submit', 'poll', 'vision', 'download'])
    p.add_argument('--round-id', required=True)
    p.add_argument('--prompt', type=Path)
    p.add_argument('--image', action='append', type=Path, default=[])
    p.add_argument('--requirements', type=Path)
    p.add_argument('--asset', action='append', choices=['image_url', 'scene_url', 'mesh_simplified_url', 'mesh_url', 'point_url', 'super_resolution_image_url'])
    a = p.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,80}', a.round_id):
        raise ValueError('Invalid round ID')
    folder = CACHE / a.round_id
    folder.mkdir(parents=True, exist_ok=True)
    state = folder / 'submission.json'
    if a.action == 'submit':
        prompt = a.prompt.read_text(encoding='utf-8').strip() if a.prompt else ''
        if not prompt or len(prompt) > 600:
            raise ValueError('Provide a reviewed text prompt of at most 600 characters')
        # Reserve before the first network byte. Ambiguous timeouts/failures consume this round too.
        attempt = {'model': 'hy-world2-scene', 'generationAttempts': 1, 'status': 'submission_started', 'prompt': prompt}
        with state.open('x', encoding='utf-8') as f:
            json.dump(attempt, f, ensure_ascii=False, indent=2)
        try:
            response = request('/wand/hunyuan-video/world2-scene', {'model': 'hy-world2-scene', 'prompt': prompt})
            task_id = response.get('id')
            if not isinstance(task_id, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,200}', task_id):
                raise ValueError('No valid task ID returned; do not resubmit')
            save(folder / 'submission-response.json', response)
            attempt.update(status='submitted', taskId=task_id)
            save(state, attempt)
            print(json.dumps({'status': 'submitted', 'taskId': task_id, 'generationAttempts': 1}))
        except Exception:
            attempt['status'] = 'submission_failed_or_unknown'
            save(state, attempt)
            raise
    elif a.action == 'poll':
        attempt = json.loads(state.read_text(encoding='utf-8'))
        task_id = attempt.get('taskId')
        if not task_id:
            raise ValueError('No saved task ID; do not resubmit an ambiguous request')
        response = request('/wand/hunyuan-video/tasks/' + urllib.parse.quote(task_id, safe=''))
        save(folder / 'task-result.json', response)
        rows = response.get('data', [])
        if isinstance(rows, dict):
            rows = [rows]
        keys = sorted({k for row in rows if isinstance(row, dict) for k in row if k.endswith('_url')})
        print(json.dumps({'status': response.get('status'), 'assetFields': keys, 'usage': response.get('tokenhub_usage')}))
    elif a.action == 'vision':
        if not a.image or len(a.image) > 6:
            raise ValueError('Provide one to six actual render screenshots')
        requirements = a.requirements.read_text(encoding='utf-8') if a.requirements else '检查画面是否构成完整、合理且可交互的卧室。'
        content = [{'type': 'text', 'text': requirements + '\n请根据实际截图审核。标签不代表物品造型已经完成；看不清的标为不确定，不要臆造背面。只返回JSON：observed(字符串数组),issues(每项含item/problem/repair),passed(布尔值)。'}]
        total = 0
        for path in a.image:
            data = path.read_bytes()
            total += len(data)
            if total > 6 * 1024 * 1024:
                raise ValueError('Screenshot input exceeds 6 MiB')
            mime = 'image/png' if data.startswith(b'\x89PNG\r\n\x1a\n') else 'image/jpeg' if data.startswith(b'\xff\xd8') else None
            if mime is None:
                raise ValueError('Screenshot must be a PNG or JPEG')
            content.append({'type': 'image_url', 'image_url': {'url': 'data:' + mime + ';base64,' + base64.b64encode(data).decode('ascii')}})
        c, _ = settings()
        response = request('/chat/completions', {'model': c['model'], 'messages': [{'role': 'system', 'content': '你是场景视觉审核员。只审核图片和给定要求，不把界面标签当作物理存在或案件事实。'}, {'role': 'user', 'content': content}], 'stream': False, 'max_tokens': 2048, 'thinking': {'type': 'disabled'}, 'response_format': {'type': 'json_object'}}, timeout=90)
        choice = response['choices'][0]
        if choice.get('finish_reason') != 'stop':
            raise ValueError('Vision response incomplete')
        review = json.loads(choice['message']['content'])
        if not isinstance(review.get('observed'), list) or not isinstance(review.get('issues'), list) or type(review.get('passed')) is not bool:
            raise ValueError('Vision response schema invalid')
        save(folder / 'vision-review.json', review)
        print(json.dumps({'model': c['model'], 'visionReview': review, 'usage': response.get('usage')}, ensure_ascii=False))
    elif a.action == 'download':
        response = json.loads((folder / 'task-result.json').read_text(encoding='utf-8'))
        if response.get('status') != 'succeeded':
            raise ValueError('Task has not succeeded; poll the existing ID, do not resubmit')
        rows = response.get('data', [])
        if isinstance(rows, dict):
            rows = [rows]
        allowed = {'image_url': '.png', 'super_resolution_image_url': '.png', 'scene_url': '.spz', 'mesh_url': '.ply', 'mesh_simplified_url': '.ply', 'point_url': '.ply'}
        for n, row in enumerate(rows):
            selected = a.asset or ['image_url', 'scene_url', 'mesh_simplified_url']
            for key, extension in allowed.items():
                if key not in selected:
                    continue
                url = row.get(key)
                if not url:
                    continue
                uri = urllib.parse.urlsplit(url)
                if uri.scheme != 'https' or not uri.hostname or not uri.hostname.endswith('.myqcloud.com') or uri.username or uri.password:
                    raise ValueError('Unexpected generated-asset host')
                target = folder / f'{n}-{key[:-4]}{extension}'
                if target.exists():
                    continue
                # Asset GETs carry no API credential; temporary signed URLs remain in ignored state only.
                try:
                    with urllib.request.build_opener(NoRedirect).open(url, timeout=60) as r, target.with_suffix(extension + '.part').open('wb') as out:
                        size = 0
                        while chunk := r.read(1024 * 1024):
                            size += len(chunk)
                            if size > 256 * 1024 * 1024:
                                raise ValueError('Generated asset exceeds 256 MiB')
                            out.write(chunk)
                    target.with_suffix(extension + '.part').replace(target)
                    print(f'Downloaded {target.name}: {size} bytes')
                except urllib.error.URLError:
                    raise RuntimeError('Asset download unavailable; signed URL withheld') from None
            if row.get('position_info'):
                save(folder / f'{n}-position-info.json', row['position_info'])

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'ERROR: {type(error).__name__}: {error}', file=sys.stderr)
        sys.exit(1)
