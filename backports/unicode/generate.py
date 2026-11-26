"""Build the Unicode 16 editing table. Pass a directory containing the three pinned UCD files, then an output path."""
import hashlib
from pathlib import Path
import struct
import sys

source, output = map(Path, sys.argv[1:])
hashes = {
    "GraphemeBreakProperty.txt":"c29360bd6f7132811d701d29069541e827eb44bfc4c8fbde8c370d6982689dc1",
    "emoji-data.txt":"f1365a5173eee18e1f98b240cdc492e84a25f1ce7e0c9d1094eb29c41a22696a",
    "DerivedCoreProperties.txt":"39d35161f2954497f69e08bdb9e701493f476a3d30222de20028feda36c1dabd",
}
classes = "Other CR LF Control L V T LV LVT Extend ZWJ SpacingMark Prepend Regional_Indicator".split()
properties = bytearray(0x110000)
for filename, expected in hashes.items():
    data = (source/filename).read_bytes()
    if hashlib.sha256(data).hexdigest()!=expected: raise ValueError(filename)
    for line in data.decode().splitlines():
        fields=[s.strip() for s in line.split("#")[0].split(";")]
        if len(fields)<2: continue
        if filename=="GraphemeBreakProperty.txt": value=classes.index(fields[1])
        elif fields[1]=="Extended_Pictographic": value=16
        elif fields[1]=="InCB": value={"Consonant":32,"Extend":64,"Linker":96}[fields[2]]
        else: continue
        limits=fields[0].split("..")
        for cp in range(int(limits[0],16),int(limits[-1],16)+1):
            properties[cp]|=value
ranges=[(i,x) for i,x in enumerate(properties) if not i or x!=properties[i-1]]
output.parent.mkdir(parents=True,exist_ok=True)
output.write_bytes(struct.pack(">II",0x55434410,len(ranges))+b"".join(struct.pack(">IB",i,v) for i,v in ranges))
