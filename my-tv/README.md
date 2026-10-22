# TCL Smart TV Pro (G08, Google TV 14): limpieza y launcher nuevo

TV: TCL `G08`, modelo "Smart TV Pro", producto `G08_4K_GB`, Android 14 (SDK 34), firmware
`UTT2.250416.001 AU02`, MediaTek `mt5896`, IP `192.168.1.68`. Hecho el 21 de octubre de 2026 por Wi-Fi
(depuración inalámbrica), sin root y sin desinstalar nada.

## Qué se hizo

- **39 paquetes deshabilitados** (`pm disable-user --user 0`): publicidad y demos de TCL, telemetría,
  servicios de anuncios de Android 14, extras, Asistente de Google, Miracast, Alexa, las apps de streaming que
  no usas, la pantalla de inicio de Google TV (`launcherx`) y el asistente de configuración (`setupwraith`).
  La lista con comentarios está en `disabled.txt`. Siguen deshabilitados después de reiniciar. FOX One MX
  (`com.calientetv`) y TCL Channel (`com.tcl.waterfall.overseas`) se rehabilitaron porque sí los usas.
- **Launcher nuevo** (`com.gathin.tvhome`) como pantalla de inicio. Dock de 7: Netflix, HBO Max, Disney+, FOX One, Prime Video, YouTube, Stremio
  (Spotify vive en el reproductor). Cuadrícula de abajo a 6 columnas, con íconos un poco más grandes que los del dock.
  Tema propio **asael** (`my-tv/themes/asael`): cielo que sigue la hora con el sol real de CDMX, dock de vidrio,
  estante cinematográfico y "BUENAS NOCHES, ASAEL". Sin línea de memoria/almacenamiento (`show_stats` en `false`).
- **Reproductor de Spotify** en Inicio (ver abajo).
- **Estilo hacker** (HackerRank) en todo el launcher: ver "Estilo del launcher".
- **Íconos redondeados** de cada app en lugar de banners: ver "Íconos de las apps".
- **DNS privado con bloqueo de anuncios** (AdGuard DNS). No bloquea los anuncios de YouTube.
- **Se dejaron activos a propósito:** Chromecast, AirPlay, actualizaciones de TCL (`com.tcl.ttvs`),
  TCL Channel, el panel de entradas, el emparejamiento del control, el teclado, Play Store y Play Services.
- El respaldo original (paquetes, ajustes, getprop) está en `backup/20261021-213511/`.

## Archivos

| Archivo | Para qué |
|---|---|
| `my-tv/restore.sh` | Deja el TV como estaba: rehabilita los 39 paquetes, regresa el inicio de Google TV y quita el DNS |
| `my-tv/build-and-install.sh` | Compila el launcher e instálalo como pantalla de inicio |
| `my-tv/skin.sh` | Cambia el estilo completo: `hacker` u `original` (recompila) |
| `my-tv/theme.sh` | Cambia el tema (fondo y colores) |
| `my-tv/wallpaper.sh` | Pone tu propia imagen de fondo, o la quita |
| `my-tv/disabled.txt` | Lista de lo deshabilitado, con comentarios |
| `launcher/app/src/main/res/values/config.xml` | Configuración del launcher: dock, botones del control, columnas |

Todos los comandos se corren desde la carpeta `cleanyourtv`.

## Antes de cualquier comando: conectar el TV

La depuración queda **apagada** cuando terminamos. Para volver a usar ADB:

1. En el TV: **Ajustes → Sistema → Opciones para desarrolladores** → activa **Depuración USB** y
   **Depuración inalámbrica**.
2. En **Depuración inalámbrica** aparece "Dirección IP y puerto", por ejemplo `192.168.1.68:40527`.
   El puerto cambia cada vez.
3. En la computadora: `adb connect 192.168.1.68:<puerto>`. Si el TV pide permiso, acepta.
4. Al terminar, apaga otra vez las dos opciones de depuración.

Los scripts encuentran el TV solos; si no lo hacen: `TV=192.168.1.68:<puerto> my-tv/restore.sh`.

## Volver a habilitar una app

**Sin computadora:** Ajustes → Aplicaciones → Ver todas las apps → Mostrar apps del sistema → elige la app →
**Habilitar**.

**Con computadora:**

```bash
adb shell pm enable com.cbs.ca        # ejemplo: Paramount+
```

Luego borra o comenta (`#`) su línea en `my-tv/disabled.txt` para que `restore.sh` la tenga en cuenta.
Nunca habilites solo `com.google.android.tungsten.setupwraith` (el asistente de configuración) mientras el
launcher nuevo es el inicio: puede quedarse con el botón Home. Para volver al inicio de Google TV usa
`restore.sh`, que lo hace en el orden correcto.

## Deshacer todo

```bash
my-tv/restore.sh --dry-run          # muestra qué haría
my-tv/restore.sh                    # rehabilita todo, inicio de Google TV y DNS de fábrica
my-tv/restore.sh --remove-launcher  # además desinstala el launcher nuevo
```

## Cambiar el orden del dock, las columnas o los botones

Edita `launcher/app/src/main/res/values/config.xml`:

- `dock_apps`: paquetes de izquierda a derecha. `adb shell pm list packages -3` lista las apps instaladas.
  Netflix `com.netflix.ninja`, YouTube `com.google.android.youtube.tv`, Max `com.wbd.stream`,
  Disney+ `com.disney.disneyplus`, Prime Video `com.amazon.amazonvideo.livingroom`,
  Crunchyroll `com.crunchyroll.crunchyroid`, ViX `com.univision.prendetv`, Spotify `com.spotify.tv.android`,
  FOX One MX `com.calientetv`, Stremio `com.stremio.one`.
- `columns`: espacios del dock (ahora 7; hasta 8, y con menos los íconos del dock crecen).
- `grid_columns`: columnas de la cuadrícula de abajo (ahora 6; con menos crecen, 0 = igual que el dock).
- `icon_tile_percent`: tamaño de los íconos dentro de su espacio (ahora 75; 100 = sin espacio entre íconos).
- `icon_only_buttons` / `inputs_icon_only`: Entradas, Wi‑Fi y Ajustes como círculos de solo ícono (ahora `true`).
- `app_keys`: qué abre cada botón del control (ver abajo).

Luego: `my-tv/build-and-install.sh`. En el TV también puedes reordenar desde el propio launcher: deja
presionado OK en **Ajustes** (la píldora de arriba a la derecha) → reordenar.

## Tema y fondo de pantalla

Con el estilo original va el tema `my-tv/themes/asael`; con el hacker, `my-tv/themes/hacker` (igual pero con fondo
`#0E141E`, sin cielo ni estante cinematográfico). El `config.json` de asael:

```text
{
  "background_color": "#05070D",   color de fondo
  "wallpaper": false,              sin imagen
  "sky_intensity": 60,             cielo que sigue la hora (0 = apagado, 100 = máximo)
  "dock_glass": true,              dock de vidrio esmerilado
  "cinematic_home": true,          arte de la app enfocada arriba
  "greeting_name": "Asael",        nombre en el saludo
  "show_stats": false              true = memoria y almacenamiento bajo el reloj
}
```

(El archivo real no lleva los comentarios de la derecha.) Edítalo y vuelve a subirlo con
`my-tv/theme.sh my-tv/themes/asael`. Todas las opciones están en `themes/README.md`. La ubicación del sol
(CDMX, 19.43 / -99.13) está en `config.xml` (`latitude`, `longitude`) y requiere `my-tv/build-and-install.sh`.

Los temas del repo están en `themes/`: `aurora`, `scenes` (14 paisajes que cambian cada hora), `dussehra`
(festivo, se apaga solo después del 21/10/2026), `six-columns` y `default`.

```bash
my-tv/theme.sh my-tv/themes/asael             # tu tema (el actual)
my-tv/theme.sh themes/aurora                 # tema aurora
my-tv/theme.sh themes/scenes                 # paisajes que rotan
my-tv/theme.sh --none                        # sin tema: negro liso
my-tv/wallpaper.sh ~/Imágenes/fondo.jpg 40   # tu propia imagen: oscura, 1920x1080, menos de ~300 KB; 40 = oscurecer 40 %
my-tv/wallpaper.sh --none                    # de vuelta a negro
```

`theme.sh` y `wallpaper.sh` se reemplazan entre sí: el último que corras es el que queda. Ninguno cambia el
dock ni los botones; eso vive en `config.xml`.

## Stremio

Instalado desde Google Play (`com.stremio.one`), con sesión iniciada en tu cuenta y la interfaz en español
(dentro de Stremio: Ajustes → General → Idioma). Los addons y la biblioteca vienen de tu cuenta de Stremio y se
sincronizan solos; se administran en stremio.com o en la sección de complementos (ícono de pieza) de la app.

## Estilo del launcher (hacker / original)

Ahora está en **estilo hacker**, inspirado en HackerRank: fondo azul marino `#0E141E`, acento verde `#00EA64`,
tipografía monoespaciada **JetBrains Mono** en todo, saludo en verde, píldoras y menús que se ponen verdes al
enfocarlos, resaltado y orilla del dock en verde, un **cursor ▌ que parpadea** después del nombre de la app y
**líneas CRT** sutiles sobre la pantalla.

```bash
my-tv/skin.sh hacker     # estilo hacker + tema my-tv/themes/hacker (el actual)
my-tv/skin.sh original   # tipografía Outfit y vidrio blanco + tema my-tv/themes/asael
```

Cada uno recompila e instala. Los colores viven en `my-tv/skins/<estilo>/skin.xml` (se copian a
`launcher/app/src/main/res/values/skin.xml`); por ejemplo `skin_accent` es el verde, `skin_scanline` la
intensidad de las líneas CRT (`#00000000` las apaga) y `skin_glow` el brillo de fondo. Edita y corre
`my-tv/skin.sh hacker` otra vez. JetBrains Mono es OFL (`launcher/FONT-LICENSE-JETBRAINS-MONO-OFL.txt`).
El cursor parpadea solo mientras Inicio está en pantalla; las líneas CRT son una imagen fija.

## Íconos de las apps

Los mosaicos son **cuadrados redondeados con el ícono de cada app** (la "N" de Netflix, el botón de YouTube, el
diamante de Stremio) en lugar de los banners horizontales con el logotipo. HBO Max, Disney+, FOX One y Prime
Video se ven con letras porque ese es su ícono oficial.

- La app de Netflix para TV no trae la "N" (su ícono es el logotipo en blanco), así que el launcher trae la suya:
  `launcher/app/src/main/res/drawable/icon_netflix.xml` (trazo de Simple Icons, CC0, con los dos rojos oficiales).
- Para cambiar el ícono de otra app: agrega un `icon_<nombre>.xml` (o `.png`) en `res/drawable` y una línea
  `paquete=icon_<nombre>` en `app_icons` de `config.xml`.
- Para volver a los banners horizontales: `icon_tiles` en `false` en `config.xml`.

Luego `my-tv/build-and-install.sh`.

## Reproductor de Spotify en Inicio

A la derecha del nombre grande, encima del dock, hay una tarjeta con lo que suena en Spotify: "SPOTIFY ·
REPRODUCIENDO" (o "EN PAUSA"), canción, artista, barra de progreso y botones ⏮ ⏯ ⏭, sin portada. Ocupa todo el
ancho que deja libre el nombre de la app enfocada: con "Netflix" se estira, con "Prime Video" se encoge, siempre
sin tapar el título. Solo la barra de progreso usa un temporizador (1 s, mientras suena y estás en Inicio).

- Desde el dock, **Arriba** entra a la tarjeta (en ▶/⏸); otra vez **Arriba** va a la barra de arriba, **Abajo**
  regresa al dock. Izquierda/Derecha se mueven entre los tres botones.
- Si Spotify no está sonando, la tarjeta dice "Nada sonando" y ▶ abre Spotify. **Mantener OK** en ▶ también abre
  Spotify.
- Al bajar a la cuadrícula se oculta.
- Lee la sesión multimedia de Spotify, para lo que el launcher tiene **acceso a notificaciones**
  (`MediaListener`; `my-tv/build-and-install.sh` lo concede y `restore.sh` lo quita). No guarda ni envía nada.
- Para usarla con otra app de música, cambia `now_playing_package` en `config.xml` (por ejemplo
  `com.google.android.youtube.tvmusic` para YouTube Music); vacío la quita. Luego `my-tv/build-and-install.sh`.
  El código está en `launcher/app/src/main/java/com/gathin/tvhome/NowPlaying.java`.

## Botones del control

TCL manda los botones de apps a la pantalla de Google TV, que está deshabilitada. Por eso los atiende el
launcher nuevo:

| Botón | Código | Funciona |
|---|---|---|
| Netflix | 4062 | Desde Inicio |
| Prime Video | 4099 | Desde Inicio |
| YouTube | 190 (`BUTTON_3`) | Desde Inicio |
| Apple TV → abre **Max** | 4160 | Desde Inicio |
| TV (TV abierta) → abre **TV en vivo** | 4001 | Desde Inicio |
| Ajustes (engrane) → abre **Ajustes** | 83 | Desde Inicio |
| Disney+, TCL Channel (TC), Entradas, Home, volumen | — | En todas partes, como siempre |

"Desde Inicio": si estás dentro de otra app, presiona **Home** primero. Que funcionen también dentro de
las apps requiere el ayudante de accesibilidad (`INPUT_KEY_SERVICE=1 my-tv/build-and-install.sh`). En este TV
eso hace que **mantener presionado el volumen suba de uno en uno**, así que quedó apagado a propósito.
Para apagarlo otra vez después de probarlo, corre `my-tv/build-and-install.sh` sin esa variable.

## DNS con bloqueo de anuncios

Activado: `private_dns_mode hostname`, `private_dns_specifier dns.adguard-dns.com`. Si una app no carga:

```bash
adb shell settings put global private_dns_mode opportunistic   # DNS normal (automático)
```

O en el TV: Ajustes → Redes e Internet → DNS privado → Automático.

## Notas de este TV

- La red Wi-Fi se borró y se volvió a guardar desde Ajustes antes de deshabilitar el asistente de
  configuración. La red original le pertenecía al asistente, y Android la habría borrado al deshabilitarlo.
- Si `launcherx` está habilitado, se queda con el botón Home aunque el launcher nuevo sea el predeterminado.
  Si una actualización de TCL lo vuelve a habilitar, corre `my-tv/build-and-install.sh`.
- Con la depuración USB encendida, el firmware abre ADB sin cifrar en el puerto 5555. Apágala cuando termines.
