#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Captura la ventana de la build de prueba, solo si es seguro. Se niega si el modo seguro está
# apagado o si en primer plano hay otra cosa (otra app, la pantalla de bloqueo o el panel de
# notificaciones). Recorta las barras del sistema, donde se ven las notificaciones.
#
#   tools/safe-capture.sh <archivo.png>
#
# Antes de cada tanda: No molestar encendido. La captura es temporal: se borra apenas se usa.
set -euo pipefail

out="${1:?falta el archivo de salida}"
pkg="dev.liquidphone.debug"
tag="LiquidPhoneDev"

check_focus() {
    # Solo la línea del foco; nada más del volcado.
    local focus
    focus=$(adb shell dumpsys window | grep -m1 'mCurrentFocus' || true)
    case "$focus" in
        *"$pkg/"*) ;;
        *) echo "NEGADO: la ventana enfocada no es $pkg" >&2; exit 2 ;;
    esac
}

check_safe() {
    adb logcat -c
    adb shell am broadcast -f 0x20 -a dev.liquidphone.DEV_STATUS -p "$pkg" >/dev/null
    local status=""
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        status=$(adb logcat -d -s "$tag:I" | grep 'DEV_STATUS' | tail -1 || true)
        [ -n "$status" ] && break
        sleep 0.3
    done
    case "$status" in
        *"safe=true"*) ;;
        *) echo "NEGADO: no se pudo confirmar el modo seguro" >&2; exit 3 ;;
    esac
}

check_focus
check_safe
check_focus

tmp="$(mktemp --suffix=.png)"
trap 'rm -f "$tmp"' EXIT
adb exec-out screencap -p > "$tmp"
# Todo lo que vaya a la imagen pasó las dos comprobaciones de foco; si cambió en el medio, se tira.
check_focus

# Barra de estado arriba y barra de navegación abajo.
python3 -I - "$tmp" "$out" <<'PY'
import sys
from PIL import Image
image = Image.open(sys.argv[1])
w, h = image.size
top = round(h * 0.052)
bottom = round(h * 0.04)
image.crop((0, top, w, h - bottom)).save(sys.argv[2])
PY
echo "$out"
