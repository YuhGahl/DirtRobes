#!/usr/bin/env python3
"""
Cris ORIGINAUX et provisoires des Spectrobes, synthétisés (aucun son tiré des jeux).

  python3 tools/sounds/gen_cries.py

- timbre selon la propriété (Corona chaud et crépitant, Aurora doux, Flash bulleux, Eclipse grave et grondant,
  Halo brillant, Prisma en cloche) ;
- hauteur et durée selon le stade (enfant aigu et court, évolué grave et long) ;
- même « signature » (forme de la mélodie, syllabes, vibrato) pour toute une lignée, petite variation par espèce.

Sorties : assets/dirtrobes/sounds/spectrobe/<id>_cry.ogg et les entrées « spectrobe.<id>.cry » de
assets/dirtrobes/sounds.json.

Remplacer un cri : déposer son propre fichier <id>_cry.ogg au même endroit (ou dans un pack de ressources).
Les fichiers qui ne viennent pas de ce script (empreinte différente de tools/sounds/generated.json) ne sont
jamais écrasés.
"""
import glob, hashlib, json, os, subprocess, tempfile, wave
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/dirtrobes")
SPECIES = os.path.join(ROOT, "src/main/resources/data/dirtrobes/spectrobes/species")
OUT = os.path.join(ASSETS, "sounds/spectrobe")
MANIFEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "generated.json")
SR = 22050
# espèces qui reprennent le cri d'une autre (même Spectrobe, autre couleur)
SAME_CRY = {"komainu_flamerax": "komainu", "komainu_mizusar": "komainu", "komainu_bombzar": "komainu",
            "komainu_optoger": "komainu", "komainu_scarazook": "komainu"}

STAGE = {"child": (880, 0.45), "adult": (600, 0.65), "evolved": (400, 0.85), "secret": (340, 0.95), "special": (300, 1.0)}


def rng_for(text):
    return np.random.default_rng(int(hashlib.sha1(text.encode()).hexdigest()[:8], 16))


def contour(kind, t):
    """Forme de la mélodie (multiplicateur de fréquence) pour t de 0 à 1."""
    if kind == 0:   # montée
        return 0.85 + 0.35 * t
    if kind == 1:   # descente
        return 1.2 - 0.4 * t
    if kind == 2:   # montée puis descente
        return 0.9 + 0.35 * np.sin(np.pi * t)
    if kind == 3:   # descente puis remontée
        return 1.1 - 0.25 * np.sin(np.pi * t)
    return 1.0 + 0.12 * np.sin(2 * np.pi * 3 * t)  # trille


def voice(prop, phase, t, f, rng):
    if prop == "corona":    # dent de scie adoucie + crépitement
        s = sum(np.sin(k * phase) / k for k in range(1, 7))
        s += 0.25 * (rng.random(len(t)) - 0.5) * (rng.random(len(t)) > 0.97)
    elif prop == "aurora":  # doux, un peu soufflé
        s = np.sin(phase) + 0.3 * np.sin(2 * phase) + 0.08 * (rng.random(len(t)) - 0.5)
    elif prop == "flash":   # bulleux (modulation d'amplitude rapide)
        s = np.sin(phase) * (0.75 + 0.25 * np.sin(2 * np.pi * 18 * t)) + 0.2 * np.sin(3 * phase)
    elif prop == "eclipse": # grave, carré, grondement
        s = np.tanh(3 * np.sin(phase)) * (0.8 + 0.2 * np.sin(2 * np.pi * 32 * t))
    elif prop == "halo":    # brillant
        s = np.sin(phase) + 0.45 * np.sin(2 * phase) + 0.3 * np.sin(4 * phase)
    else:                   # prisma : cloche
        s = np.sin(phase) + 0.5 * np.sin(2.76 * phase) + 0.3 * np.sin(5.4 * phase)
    return s


def synth(sid, d):
    stage, prop, fam = d["stage"], d["property"], d.get("family", sid)
    base, dur = STAGE.get(stage, STAGE["adult"])
    fr, sr_ = rng_for("famille:" + fam), rng_for("espece:" + sid)
    kind = int(fr.integers(0, 5))
    syllables = int(fr.integers(1, 4))
    vib_rate, vib_depth = fr.uniform(4, 9), fr.uniform(0.0, 0.04)
    base *= fr.uniform(0.9, 1.12) * sr_.uniform(0.94, 1.06)
    dur *= sr_.uniform(0.9, 1.1)
    n = int(SR * dur)
    t = np.arange(n) / SR
    u = t / dur
    # syllabes : petites coupures entre les sons
    seg = np.floor(u * syllables)
    within = u * syllables - seg
    f = base * contour(kind, u) * (1 + vib_depth * np.sin(2 * np.pi * vib_rate * t))
    f *= 1 + 0.06 * (seg % 2)  # syllabes alternées un peu plus hautes
    phase = 2 * np.pi * np.cumsum(f) / SR
    s = voice(prop, phase, t, f, sr_)
    env = np.minimum(1, within / 0.06) * np.minimum(1, (1 - within) / 0.25) if syllables > 1 else \
        np.minimum(1, u / 0.04) * np.minimum(1, (1 - u) / 0.35)
    s = s * env * np.exp(-1.2 * u)
    s /= max(1e-6, np.max(np.abs(s)))
    return (s * 0.7 * 32767).astype(np.int16)


def write_ogg(samples, path):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(samples.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "3", path], check=True)
    os.remove(tmp.name)


def sha(path):
    return hashlib.sha1(open(path, "rb").read()).hexdigest()


def main():
    os.makedirs(OUT, exist_ok=True)
    manifest = json.load(open(MANIFEST)) if os.path.exists(MANIFEST) else {}
    sounds_path = os.path.join(ASSETS, "sounds.json")
    sounds = json.load(open(sounds_path, encoding="utf-8")) if os.path.exists(sounds_path) else {}
    made = kept = 0
    for f in sorted(glob.glob(os.path.join(SPECIES, "*.json"))):
        sid = os.path.basename(f)[:-5]
        d = json.load(open(f, encoding="utf-8"))
        out = os.path.join(OUT, f"{sid}_cry.ogg")
        if sid in SAME_CRY:
            # pas de fichier à lui : il joue le cri de l'espèce indiquée (on retire un ancien cri généré)
            if os.path.exists(out) and manifest.get(sid) == sha(out):
                os.remove(out)
                manifest.pop(sid, None)
            src = SAME_CRY[sid]
            sounds[f"spectrobe.{sid}.cry"] = {"sounds": [{"name": f"dirtrobes:spectrobe/{src}_cry", "volume": 0.8}],
                                              "subtitle": "subtitles.dirtrobes.spectrobe.cry"}
            continue
        if os.path.exists(out) and manifest.get(sid) != sha(out):
            kept += 1  # cri fourni par l'utilisateur : on n'y touche pas
        elif not os.path.exists(out):
            write_ogg(synth(sid, d), out)
            manifest[sid] = sha(out)
            made += 1
        sounds[f"spectrobe.{sid}.cry"] = {"sounds": [{"name": f"dirtrobes:spectrobe/{sid}_cry", "volume": 0.8}],
                                          "subtitle": "subtitles.dirtrobes.spectrobe.cry"}
    with open(sounds_path, "w", encoding="utf-8") as fh:
        json.dump(sounds, fh, indent=2, ensure_ascii=False)
    with open(MANIFEST, "w") as fh:
        json.dump(manifest, fh, indent=0, sort_keys=True)
    print(f"cris : {made} générés, {kept} fournis par toi conservés, {len(manifest)} au total")


if __name__ == "__main__":
    main()
