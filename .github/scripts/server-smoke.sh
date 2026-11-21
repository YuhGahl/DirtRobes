#!/usr/bin/env bash
# Démarre un serveur Minecraft avec le mod, attend qu'il soit prêt, puis l'arrête.
# Échoue si le serveur ne démarre pas ou si le log contient des erreurs liées au mod.
set -u
mkdir -p run
echo "eula=true" > run/eula.txt
LOG=server.log
rm -f "$LOG" /tmp/srv_in
mkfifo /tmp/srv_in
./gradlew runServer --console=plain < /tmp/srv_in > "$LOG" 2>&1 &
PID=$!
exec 3>/tmp/srv_in

ready=0
for i in $(seq 1 600); do
  if grep -q "Done (" "$LOG"; then ready=1; break; fi
  if ! kill -0 "$PID" 2>/dev/null; then break; fi
  sleep 1
done

if [ "$ready" = 1 ]; then
  echo "summon dirtrobes:spectrobe 0 100 0" >&3
  sleep 5
  # pont Cobblemon : toutes les espèces chargées côté Cobblemon + aller-retour Spectrobe → Pokémon → Spectrobe
  if ls run/mods | grep -qi cobblemon; then echo "dirtrobes cobblemon check" >&3; sleep 5; fi
  echo "stop" >&3
  for i in $(seq 1 90); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
fi
kill "$PID" 2>/dev/null; exec 3>&-

echo "================ Résumé ================"
grep -E "espèces de Spectrobes|Done \(|curios|Curios|Intégration" "$LOG" | grep -iE "espèces|Done|slot|Intégration" | grep -v DEBUG | head -15 || true
ERRS=$(grep -nE "ERROR|Exception|Caused by|Failed to|Couldn't|Unknown|Invalid" "$LOG" \
  | grep -iE "dirtrobes|spectrobe|biome_modifier|worldgen|loot|recipe|tags?" \
  | grep -v "forge.logging" || true)
if [ -n "$ERRS" ]; then
  echo "---- Erreurs liées au mod ----"; echo "$ERRS" | head -80
fi
if [ "$ready" != 1 ]; then
  echo "Le serveur n'a pas démarré."; tail -120 "$LOG"; exit 1
fi
[ -z "$ERRS" ] || exit 1
if ls run/mods | grep -qi cobblemon && ! grep -q "Intégration Cobblemon : .* aller-retour OK" "$LOG"; then
  echo "Le pont Cobblemon n'a pas été validé."; grep -n "Cobblemon" "$LOG" | tail -30; exit 1
fi
echo "Serveur démarré sans erreur liée au mod."
