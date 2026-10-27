"""Tests the freshly deployed AI (spec 017) with temporary accounts that are deleted at the end.
Measures how long the first sentence and the first audio take. Run by the «Función de IA» workflow after deploying."""
import json, secrets, struct, sys, time, datetime, urllib.request, urllib.error

KEY = json.load(open('app/google-services.json'))['client'][0]['api_key'][0]['current_key']
FS = 'https://firestore.googleapis.com/v1/projects/earcaster/databases/(default)/documents'
FN = 'https://us-central1-earcaster.cloudfunctions.net/'
VIDEO_VOICE = '2Lb1en5ujrODDIqmp7F3'


def http(method, url, body=None, token=None, raw=False):
    h = {'Content-Type': 'application/json'}
    if token: h['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(url, method=method, headers=h,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            data = r.read()
            return r.status, (data if raw else json.loads(data or b'{}'))
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:300]


def signup(body):
    st, r = http('POST', 'https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=' + KEY, body)
    assert st == 200, (st, r)
    return r['idToken'], r['localId']


def ts(dt): return dt.strftime('%Y-%m-%dT%H:%M:%S.%fZ')


def stream(token, body):
    """Calls responder and reads the frames as they arrive, with their timings."""
    req = urllib.request.Request(FN + 'responder', method='POST', data=json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
    t0 = time.time()
    out = {'texts': [], 'audio': 0, 'first_text': None, 'first_audio': None, 'action': None, 'end': None}
    try:
        r = urllib.request.urlopen(req, timeout=60)
    except urllib.error.HTTPError as e:
        out['status'] = e.code
        return out
    out['status'] = r.status
    while True:
        head = r.read(5)
        if len(head) < 5: break
        kind, n = chr(head[0]), struct.unpack('>I', head[1:])[0]
        data = r.read(n)
        now = time.time() - t0
        if kind == 'T':
            out['texts'].append(data.decode())
            out['first_text'] = out['first_text'] or now
        elif kind == 'A':
            out['audio'] += len(data)
            out['first_audio'] = out['first_audio'] or now
        elif kind == 'X':
            out['action'] = json.loads(data)
        elif kind == 'E':
            out['end'] = json.loads(data)
    out['total'] = time.time() - t0
    return out


cg_tok, cg_uid = signup({'email': 'prueba-ia-%s@example.com' % secrets.token_hex(4),
                         'password': secrets.token_urlsafe(12), 'returnSecureToken': True})
ph_tok, ph_uid = signup({'returnSecureToken': True})
pid = 'pruebaia' + secrets.token_hex(4)
code = 'ZZ' + secrets.token_hex(3).upper().replace('0', 'Z').replace('1', 'Y')
now = datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
day = datetime.date.today().isoformat()
config = json.dumps({'triggers': '[]', 'ai_on': True, 'ai_name': 'Clara', 'ai_max': 10})
facts = {'now': 'lunes 19 de octubre de 2026, las 6 y media de la tarde',
         'visits': ['Ana: mañana martes 20 de octubre a las 5 en punto de la tarde'], 'doses': []}
ok = True
try:
    st, _ = http('POST', FS + '/patients?documentId=' + pid, {'fields': {
        'caregiverUid': {'stringValue': cg_uid}, 'name': {'stringValue': 'Prueba IA'},
        'config': {'stringValue': config}, 'aiConsentAt': {'timestampValue': ts(now)}}}, cg_tok)
    assert st == 200, st
    http('PATCH', FS + '/patients/' + pid + '/private/profile', {'fields': {
        'callMe': {'stringValue': 'Mari'}, 'family': {'stringValue': 'Su hija Ana vive cerca. Su hijo Carlos vive en Madrid.'},
        'calls': {'arrayValue': {'values': [{'mapValue': {'fields': {
            'name': {'stringValue': 'Carlos'}, 'number': {'stringValue': '+34600000000'}, 'relation': {'stringValue': 'su hijo'}}}}]}},
        'help': {'booleanValue': False}, 'light': {'booleanValue': True}}}, cg_tok)
    http('POST', FS + '/links?documentId=' + code, {'fields': {
        'pid': {'stringValue': pid}, 'caregiverUid': {'stringValue': cg_uid},
        'expiresAt': {'timestampValue': ts(now + datetime.timedelta(minutes=10))}}}, cg_tok)
    st, _ = http('POST', FS + '/patients/' + pid + '/devices?documentId=' + ph_uid,
                 {'fields': {'code': {'stringValue': code}}}, ph_tok)
    assert st == 200, st

    # Right after the function is created, Google takes a while to open it up and responds
    # with its own 401/403 page. It waits up to 3 minutes for the function to answer.
    for _ in range(18):
        st, v = http('GET', FN + 'voices', None, cg_tok)
        if st not in (401, 403) or b'<html' not in v: break
        time.sleep(10)
    print('voces', st, [x['name'] for x in v['voices'][:5]] if st == 200 else v)
    ok = ok and st == 200
    # With no voice chosen, Clara's voice from the landing video must play (otherwise the function uses the fallback
    # and leaves in its logs why ElevenLabs rejects it).
    video = st == 200 and v.get('default') == VIDEO_VOICE
    print('voz por defecto', 'la del video' if video else 'la de respaldo: %s' % (v.get('default') if st == 200 else '?'))
    ok = ok and video

    st, p = http('POST', FN + 'preview', {'pid': pid, 'text': '¿viene Ana hoy?', 'day': day, 'facts': facts}, cg_tok)
    print('vista previa', st, p)
    ok = ok and st == 200

    for q in ['ana viene hoy', 'quién eres', 'llama a mi hijo', 'cuándo viene carlos']:
        r = stream(ph_tok, {'pid': pid, 'text': q, 'day': day, 'facts': facts})
        print(f"«{q}» -> {r['status']} {r['texts']} acción={r['action']} fin={r['end']} "
              f"texto={r['first_text'] and round(r['first_text'], 2)}s audio={r['first_audio'] and round(r['first_audio'], 2)}s "
              f"total={r.get('total') and round(r['total'], 2)}s bytes={r['audio']}")
        ok = ok and r['status'] == 200 and bool(r['end'] and r['end'].get('ok'))
        # A spoken reply must arrive with audio.
        if r['texts']: ok = ok and bool(r['end'].get('voice')) and r['audio'] > 0

    # The same patient with Meta's Muse Spark (spec 018).
    meta = json.dumps({**json.loads(config), 'ai_model': 'meta'})
    st, _ = http('PATCH', FS + '/patients/' + pid + '?updateMask.fieldPaths=config',
                 {'fields': {'config': {'stringValue': meta}}}, cg_tok)
    assert st == 200, st
    for q in ['ana viene hoy', 'llama a mi hijo']:
        r = stream(ph_tok, {'pid': pid, 'text': q, 'day': day, 'facts': facts})
        print(f"Meta «{q}» -> {r['status']} {r['texts']} acción={r['action']} fin={r['end']} "
              f"texto={r['first_text'] and round(r['first_text'], 2)}s total={r.get('total') and round(r['total'], 2)}s")
        ok = ok and r['status'] == 200 and bool(r['end'] and r['end'].get('ok'))
finally:
    st, q = http('GET', FS + '/patients/' + pid + '/talks', None, cg_tok)
    for d in (q.get('documents', []) if isinstance(q, dict) else []):
        http('DELETE', 'https://firestore.googleapis.com/v1/' + d['name'], None, cg_tok)
    st, q = http('GET', FS + '/patients/' + pid + '/usage', None, cg_tok)
    for d in (q.get('documents', []) if isinstance(q, dict) else []):
        http('DELETE', 'https://firestore.googleapis.com/v1/' + d['name'], None, cg_tok)
    http('DELETE', FS + '/links/' + code, None, cg_tok)
    http('DELETE', FS + '/patients/' + pid + '/private/profile', None, cg_tok)
    http('DELETE', FS + '/patients/' + pid + '/devices/' + ph_uid, None, cg_tok)
    st, _ = http('DELETE', FS + '/patients/' + pid, None, cg_tok)
    print('limpieza', st)
    for t in (cg_tok, ph_tok):
        http('POST', 'https://identitytoolkit.googleapis.com/v1/accounts:delete?key=' + KEY, {'idToken': t})
print('TODO BIEN' if ok else 'HAY FALLOS')
sys.exit(0 if ok else 1)
