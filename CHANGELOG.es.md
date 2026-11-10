<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Registro de cambios

[English](CHANGELOG.md) | **Español**

## Sin publicar

### Cambios
- En la llamada, la foto del contacto es un poco más chica: hasta 240 dp o dos tercios del ancho.
- Suena un "bloop" corto al terminar una llamada, corte quien corte (no al rechazar ni en las perdidas).

## 1.2.2

### Cambios
- En la llamada, la foto del contacto es más grande: hasta 280 dp o tres cuartos del ancho.

## 1.2.1

### Cambios
- Íconos nuevos. El predeterminado (Liquid) tiene un fondo grafito, más sobrio, en vez del brillo celeste y rosa, y su detalle de color ahora es el mismo rojo en toda la suite. El de estilo Material es de un color pleno con el dibujo en blanco y más chico, sin el marco celeste.
- Ícono nuevo de estilo Samsung.
- Ajustes: "Color de énfasis" ahora es solo "Énfasis".

## 1.2.0

### Novedades
- Recientes: un botón para borrar el historial de llamadas. Con un filtro puesto, borra solo lo que se ve. Siempre pregunta antes y dice cuántas llamadas son.
- Recientes: selección múltiple. Se empieza manteniendo apretada una entrada; el detalle sigue en el botón (i).

### Cambios
- Teclado: teclas más altas y números más grandes, con una animación chica al apretarlos.
- Recientes: la ventana de filtros es nueva, con un grupo para el tipo de llamada y otro para el chip, una opción por grupo y un botón que dice cuántas llamadas se van a ver.

## 1.1.1

### Cambios
- Íconos Samsung e iPhone nuevos. Se saca el ícono Motorola: quien lo tenía pasa a Liquid.
- Recientes: los filtros van en una ventana flotante, con un botón al lado de Ajustes.
- Llamada en horizontal: colgar va al lado de los demás botones y la foto ya no queda cortada.

### Arreglado
- La llamada entrante abre su pantalla también con el celu bloqueado, apagado o en uso. Antes, en apps instaladas fuera de una tienda, solo aparecía la notificación.

## 1.1.0

### Agregado
- Teclado con T9 en español, recientes del registro del sistema, favoritos y contactos.
- Doble SIM: política de chip, dos botones de llamar, chip y filtro en recientes, devolver con el chip original. Nunca hay un cambio silencioso de chip.
- Chip principal elegible en Ajustes, solo para esta app: define la política por defecto, el botón destacado y a quién se le pide confirmar si no está disponible.
- Mejor contraste en tema claro: el texto sobre color siempre llega a 4,5:1. Es una excepción local respecto de la suite.
- App de teléfono opcional (rol de marcador): llamada entrante sobre la pantalla de bloqueo con el chip, llamada en curso (silenciar, audio, retener, tonos, agregar, cambiar, combinar, colgar), sensor de proximidad, notificaciones, llamada perdida con el chip y bloqueo de números con la lista del celu. Permisos nuevos: `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` y `WAKE_LOCK`.
- Contactos de prueba, solo en debug y profile.
- Título y botón de agregar en el teclado; el chip para llamar con un toque se elige en una ventana flotante; mantener apretado borrar limpia el número con una animación breve; con un solo chip no se ofrece elegir.
- Cambio de pestaña más rápido al tocar la barra.
- Llamada entrante nueva: foto del contacto, nombre más abajo y deslizar para atender o rechazar. Atender queda a la derecha.
- Teclado y botones de SIM y llamar de cristal en Liquid Glass, con el bloque más centrado.
- Llamada en curso: foto en el centro, botones de cristal, teclado de tonos oculto por defecto y animado, y sobre la foto en Liquid Glass.
- Aviso en el teclado para usar esta como la app de teléfono: sin eso, las llamadas se abren en la que lo sea.
- Ficha de contacto completa, con el diseño de Liquid Contacts. La estrella, abrir y editar llevan a Contacts.
