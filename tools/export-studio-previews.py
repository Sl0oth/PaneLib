"""Transcode actual dev-client screenshot sequences into small review GIFs. Requires Pillow."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / "pane-demo/run/screenshots"
output = root / "docs/previews"
output.mkdir(parents=True, exist_ok=True)
for prefix, expected in (("atlas-motion", 91), ("atlas-hud", 38)):
    paths = sorted(source.glob(prefix + "-*.png"))
    if len(paths) != expected:
        raise SystemExit(f"Expected {expected} client frames for {prefix}; found {len(paths)}")
    def load_frame(path):
        with Image.open(path) as image:
            rgb = image.convert("RGB")
            if prefix == "atlas-hud":
                # Close-up of the actual live HUD, avoiding unrelated command feedback/chat.
                return rgb.crop((max(0, image.width - 560), 0, image.width, min(320, image.height)))
            return rgb
    samples = Image.new("RGB", (320, 180 * 3))
    for index, path in enumerate((paths[0], paths[len(paths)//2], paths[-1])):
        samples.paste(load_frame(path).resize((320, 180)), (0, index * 180))
    palette = samples.quantize(colors=192)
    frames = []
    for path in paths:
        image = load_frame(path)
        width = 840 if prefix == "atlas-hud" else 960
        height = round(image.height * width / image.width)
        resized = image.resize((width, height), Image.Resampling.LANCZOS)
        frames.append(resized.quantize(palette=palette, dither=Image.Dither.NONE))
    durations = [50] * len(frames)  # Captured at the real client's 20 Hz test tick cadence.
    durations[-1] = 650
    target = output / (prefix + ".gif")
    frames[0].save(target, save_all=True, append_images=frames[1:], duration=durations,
                   loop=0, optimize=True, disposal=1)
    with Image.open(target) as result:
        print(f"{target.name}: {result.n_frames} encoded frames, {target.stat().st_size:,} bytes")
