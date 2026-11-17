# DirtRobes 0.1.0 (NeoForge 1.21.1)

Mod fan inspiré de Spectrobes, construit sur le modèle de Cobblemon : les espèces sont des fichiers
JSON (datapack), les modèles et les animations sont au format Bedrock / Blockbench (resource pack),
et le rendu passe par un moteur maison (pas de GeckoLib).

## Compiler le mod

Il faut un JDK 21 installé (par exemple Eclipse Temurin 21).

```
gradlew build          (Windows : gradlew.bat build)
```

Le premier lancement télécharge Minecraft, NeoForge 21.1.266 et Gradle : ça prend quelques minutes.
Le fichier du mod arrive dans `build/libs/dirtrobes-0.1.0.jar`. Il suffit de le copier dans le dossier
`mods` d'une instance NeoForge 1.21.1 (par exemple l'instance Cobblemon).

Pour tester sans installer : `gradlew runClient`.

## Contenu de la v0.1

- **144 Spectrobes** (jeu DS + Origins), avec les stats et propriétés du document de conception.
- **6 propriétés** : Corona > Aurora > Eclipse > Halo > Flash > Corona, Prisma neutre (×1,5 / ×0,67).
  Les mobs vanilla ont une propriété via les tags `data/dirtrobes/tags/entity_type/property/`.
- **Stats façon Pokémon sans IV** : PV/ATQ/DÉF calculés depuis la stat de base, le niveau et les EV
  (252 par stat, 510 au total), puis convertis en vie, dégâts et armure.
- **Niveaux et évolutions** : enfant → adulte au niveau 10, adulte → évolué au niveau 30 (réglable par
  espèce dans son JSON avec `evolution_level`).
- **Compagnons** : suivent le joueur, s'assoient, dorment la nuit quand ils sont assis, tombent K.O.
  au lieu de mourir (récupération en 1 minute).
- **Combat en temps réel** : les adultes et évolués attaquent ce que tu attaques ou ce qui t'attaque.
  Touche **R** : attaque spéciale (ruée + coup ×2,2, recharge 5 s) sur la cible ou ce que tu regardes.
- **Fouilles** : pierre et ardoise à fossiles dans le sol (de y=-48 à y=100). Les enfants qui te suivent
  repèrent les fossiles autour d'eux (particules + message), selon leur portée de recherche.
- **Fossiles** (un par propriété) à éveiller dans l'**incubateur** → un enfant de cette propriété, apprivoisé.
- **Minéraux** Power / Defence / Health (C, B, A, A+) : de l'XP et des EV pour ton Spectrobe.
- **Prizmod** : fiche du Spectrobe, assis / suivre, rappel de tous tes Spectrobes.
- **Commande de test** : `/dirtrobes spawn <espèce> [niveau]`.

Recettes :
- Incubateur : fer, verre et éclat d'améthyste (fer en haut et en bas, verre sur les côtés, améthyste au centre).
- Prizmod : vitre, redstone et fer.

## Ajouter un vrai modèle à un Spectrobe

Les 144 Spectrobes utilisent pour l'instant deux **modèles provisoires** (créatures originales, teintées
de la couleur de leur propriété). Pour donner son propre modèle à un Spectrobe, par exemple Komainu :

1. Dans Blockbench (« Bedrock Entity »), fais le modèle et ses animations. Garde les noms d'os
   `body`, `head`, `front_leg_left`, `front_leg_right`, `back_leg_left`, `back_leg_right`, `tail`, `eye_left`, `eye_right`.
2. Place les fichiers dans `assets/dirtrobes/` :
   - `bedrock/spectrobes/models/komainu.geo.json`
   - `bedrock/spectrobes/animations/komainu.animation.json` (animations `animation.komainu.ground_idle`, etc.)
   - `bedrock/spectrobes/posers/komainu.json` (copie de `placeholder_child.json` en remplaçant le nom)
   - `textures/spectrobes/komainu.png`
3. Ajoute `bedrock/spectrobes/resolvers/komainu.json` :

```json
{
  "species": "dirtrobes:komainu",
  "model": "dirtrobes:komainu",
  "texture": "dirtrobes:textures/spectrobes/komainu.png",
  "poser": "dirtrobes:komainu",
  "tint": false
}
```

Ça marche aussi depuis un simple resource pack, sans recompiler le mod.

## Ajouter ou modifier une espèce

Un fichier par espèce dans `data/dirtrobes/spectrobes/species/` :

```json
{
  "name": "Kasumi",
  "property": "flash",
  "stage": "child",
  "family": "kasumi",
  "search_range": 6,
  "base_stats": { "hp": 41, "attack": 69, "defense": 37 },
  "evolves_into": "dirtrobes:kasumite",
  "evolution_level": 10
}
```

Un datapack peut ajouter ou remplacer des espèces de la même façon.
