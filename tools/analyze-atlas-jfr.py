"""Summarize JFR CPU/allocation samples within Atlas's marked measured phases."""
import collections, datetime, json, re, sys
from pathlib import Path

def instant(value):
    return datetime.datetime.fromisoformat(value).timestamp()
def duration(value):
    match = re.fullmatch(r'PT(?:(\d+)H)?(?:(\d+)M)?([\d.]+)S', value)
    return int(match[1] or 0)*3600+int(match[2] or 0)*60+float(match[3])
events=json.loads(Path(sys.argv[1]).read_text())['recording']['events']
phases=[]
for event in events:
    if event['type']=='dev.pane.AtlasPhase' and event['values']['phase'] in ('ui-measured','hud-measured'):
        v=event['values']; start=instant(v['startTime']);phases.append((start,start+duration(v['duration']),v['phase']))
for phase in ('ui-measured','hud-measured'):
    leaf=collections.Counter(); pane=collections.Counter(); categories=collections.Counter(); alloc=collections.Counter(); count=0
    for event in events:
        v=event['values']; t=instant(v['startTime'])
        if not any(a<=t<=b and p==phase for a,b,p in phases):continue
        thread=v.get('sampledThread',v.get('eventThread',{})) or {}
        if thread.get('javaName') not in ('Render thread','main'):continue
        frames=(v.get('stackTrace') or {}).get('frames',[])
        names=[f['method']['type']['name']+'.'+f['method']['name'] for f in frames]
        if event['type']=='jdk.ObjectAllocationSample':
            if any(n.startswith('dev/pane/') for n in names):alloc[v['objectClass']['name']]+=v.get('weight',0)
            continue
        if event['type'] not in ('jdk.ExecutionSample','jdk.NativeMethodSample') or not names:continue
        count+=1;leaf[names[0]]+=1
        owner=next((n for n in names if n.startswith('dev/pane/')),None)
        if owner:pane[owner]+=1
        if any('sun/java2d' in n for n in names):categories['Java2D']+=1
        elif any('UiPixelTransfer' in n for n in names):categories['pixel transfer']+=1
        elif any('GlCommandEncoder' in n or 'org/lwjgl/opengl' in n for n in names):categories['GL upload/draw/native']+=1
        elif owner:categories['Pane other']+=1
        else:categories['Minecraft/wait/other']+=1
    print('\nPHASE',phase,'samples',count,'categories',dict(categories))
    print('TOP LEAVES',*leaf.most_common(12),sep='\n')
    print('PANE CALLERS',*pane.most_common(12),sep='\n')
    print('PANE ALLOCATION WEIGHT',*alloc.most_common(10),sep='\n')
