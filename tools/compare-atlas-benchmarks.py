"""Compare Atlas's raw client CSVs. Usage: python3 tools/compare-atlas-benchmarks.py before-label after-label."""
import csv, json, math, sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
directory = root / 'pane-demo' / 'run-benchmark'
def summarize(label, scene):
    with (directory / f'{label}-{scene}.csv').open() as source:
        rows = [{key: int(value) for key, value in row.items()} for row in csv.DictReader(source)]
    paints = [row for row in rows if row['rasterNanos'] > 0]
    intervals = [row['frameIntervalNanos'] for row in rows if row['frameIntervalNanos'] > 0]
    def percentile(values, q):
        values = sorted(values)
        return values[min(len(values)-1, math.ceil(len(values)*q)-1)] / 1e6 if values else 0
    seconds = sum(intervals) / 1e9
    return dict(frames=len(rows), paints=len(paints), sampledSeconds=seconds,
        frameMs={str(q): percentile(intervals, q) for q in (.5,.95,.99)},
        extractMs={str(q): percentile([r['extractNanos'] for r in rows],q) for q in (.5,.95,.99)},
        paintExtractMs={str(q): percentile([r['extractNanos'] for r in paints],q) for q in (.5,.95,.99)},
        rasterMs={str(q): percentile([r['rasterNanos'] for r in paints],q) for q in (.5,.95,.99)},
        uploadMs={str(q): percentile([r['uploadNanos'] for r in paints],q) for q in (.5,.95,.99)},
        meanRasterMs=sum(r['rasterNanos'] for r in paints)/max(1,len(paints))/1e6,
        uploadedPixels=sum(r['uploadPixels'] for r in rows),
        uploadMiBPerSecond=sum(r['uploadPixels'] for r in rows)*4/max(.001,seconds)/2**20,
        allocatedBytes=sum(r['allocatedBytes'] for r in rows),
        allocatedBytesPerFrame=sum(r['allocatedBytes'] for r in rows)/max(1,len(rows)),
        allocatedMiBPerSecond=sum(r['allocatedBytes'] for r in rows)/max(.001,seconds)/2**20)
print(json.dumps({label: {scene:summarize(label,scene) for scene in ('ui','hud')} for label in sys.argv[1:]}, indent=2))
