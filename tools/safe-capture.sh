#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Captura la ventana de la build de prueba. Se niega si en primer plano hay otra cosa (otra app, la
# pantalla de bloqueo o el panel de notificaciones). Recorta las barras del sistema, donde se ven
# las notificaciones. La app muestra datos reales: no capturar Recientes ni el historial de un
# contacto si hay llamadas reales, salvo que el dueño del celu lo pida.
#
#   tools/safe-capture.sh <archivo.png>
#
# Antes de cada tanda: No molestar encendido. La captura es temporal: se borra apenas se usa.
set -euo pipefail

out="${1:?falta el archivo de salida}"
pkg="dev.liquidphone.debug"

check_focus() {
    # Solo la línea del foco; nada más del volcado.
    local focus
    focus=$(adb shell dumpsys window | grep -m1 'mCurrentFocus' || true)
    case "$focus" in
        *"$pkg/"*) ;;
        *) echo "NEGADO: la ventana enfocada no es $pkg" >&2; exit 2 ;;
    esac
}

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
# En horizontal el 4 % de la altura no alcanza para tapar la barra de estado.
if w > h:
    sys.exit("NEGADO: el celu está en horizontal")
top = round(h * 0.04)
bottom = round(h * 0.04)
image.crop((0, top, w, h - bottom)).save(sys.argv[2])
PY
echo "$out"
