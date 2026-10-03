"""Generates assets/ktp_sample.jpg: a FICTIONAL e-KTP-like card photographed on a dark desk.

All data is invented. The card is watermarked "CONTOH" (sample) and is not a real or usable document.
Requires Pillow. Run from the repo root: python3 samples/tools/make_sample_ktp.py
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

A = Path(__file__).resolve().parent.parent / "assets"
FONT = "/System/Library/Fonts/Helvetica.ttc"
f = lambda s, i=0: ImageFont.truetype(FONT, s, index=i)

W, H = 1011, 638
card = Image.new("RGB", (W, H))
px = card.load()
for y in range(H):
    for x in range(W):
        t = (x / W * 0.6 + y / H * 0.4)
        px[x, y] = (int(176 + 40 * t), int(214 + 22 * t), int(238 - 28 * t))
d = ImageDraw.Draw(card)
ink = (28, 38, 58)
d.text((W // 2, 34), "PROVINSI CONTOH BARAT", font=f(34, 1), fill=ink, anchor="mt")
d.text((W // 2, 78), "KOTA CONTOH", font=f(34, 1), fill=ink, anchor="mt")
d.text((40, 150), "NIK", font=f(30, 1), fill=ink)
d.text((200, 150), ": 3200000000000001", font=f(40, 1), fill=ink)
rows = [("Nama", "BUDI CONTOH"), ("Tempat/Tgl Lahir", "CONTOH, 01-01-1990"),
        ("Jenis Kelamin", "LAKI-LAKI"), ("Alamat", "JL. CONTOH NO. 1"),
        ("   RT/RW", "001/002"), ("   Kel/Desa", "CONTOHSARI"),
        ("Agama", "ISLAM"), ("Pekerjaan", "PELAJAR/MAHASISWA")]
y = 215
for k, v in rows:
    d.text((40, y), k, font=f(25), fill=ink)
    d.text((290, y), ": " + v, font=f(25, 1), fill=ink)
    y += 44
# portrait (right side, KTP layout), taken from the bundled sample selfie
face = Image.open(A / "face_b.jpg").crop((120, 330, 960, 1450)).resize((260, 347))
face = Image.eval(face, lambda p: p)
card.paste(face, (700, 160))
d.text((830, 530), "01-01-2030", font=f(22), fill=ink, anchor="mt")
# CONTOH watermark
wm = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(wm).text((W // 2, H // 2 + 60), "CONTOH", font=f(150, 1), fill=(200, 40, 40, 38), anchor="mm")
card = Image.alpha_composite(card.convert("RGBA"), wm.rotate(18)).convert("RGB")

# photograph it: dark desk, slight rotation, soft shadow
random.seed(7)
bg = Image.new("RGB", (1800, 1300), (38, 41, 48))
bpx = bg.load()
for _ in range(120000):
    x, y = random.randrange(1800), random.randrange(1300)
    n = random.randint(-9, 9)
    r, g, b = bpx[x, y]
    bpx[x, y] = (r + n, g + n, b + n)
card = card.resize((1250, 789))
rc = card.convert("RGBA").rotate(-5, expand=True, resample=Image.BICUBIC)
shadow = Image.new("RGBA", rc.size, (0, 0, 0, 0))
shadow.paste((0, 0, 0, 150), mask=rc.split()[3])
shadow = shadow.filter(ImageFilter.GaussianBlur(18))
ox, oy = (1800 - rc.width) // 2, (1300 - rc.height) // 2
bg.paste(shadow, (ox + 14, oy + 22), shadow)
bg.paste(rc, (ox, oy), rc)
bg.save(A / "ktp_sample.jpg", quality=88)
print("wrote", A / "ktp_sample.jpg")
