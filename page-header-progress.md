# Progreso: page-header

## Estado

- [x] Plan escrito (este fichero).
- [x] `Viewport.kt` + test: ancho/escala en función pura, con test JVM (pasa).
- [x] Cabecera y pestaña; WebView sin pull-to-refresh; fuera `swiperefreshlayout`.
- [x] Docs: `CLAUDE.md` (cabecera, por qué no hay pull-to-refresh, la trampa del
      viewport fijado al cargar) y README.
- [x] Verificado en el DT50 (build debug), con ItsMyMoney y Jackery: abre con la
      cabecera oculta; la pestaña abre con un toque y se arrastra sin salirse;
      zoom y escritorio re-maquetan sin recargar; recargar recarga (un campo
      escrito se vacía); arrastrar hacia abajo no hace nada; pellizco táctil real;
      `+` tras pellizcar devuelve la escala; rotar conserva todo y reajusta el
      ancho; tema oscuro; icono de la web en la cabecera.
- [ ] Pendiente: que Edgar lo pruebe.

**Hallazgo durante la verificación:** con el zoom distinto de 100 %, tras una
recarga o una navegación el pellizco quedaba muerto hasta cerrar la página.
Causa: el script fijaba la escala (mín = máx) en `onPageFinished`, y Chromium
se queda con esos límites. Arreglado: al cargar se aplica el viewport ya
liberado, sin fijar; el fijado solo se usa al tocar la cabecera o al rotar.

---

# Cabecera de página (zoom, escritorio, recargar) y fuera el pull-to-refresh

## Contexto

Hoy una página abierta es solo la WebView a pantalla completa; recargar se hace
tirando hacia abajo (`PullToRefreshLayout`, que costó tres versiones afinar). Se
quiere la barra de `claude-history-android` (`ui/ViewerScreen.kt`: `ViewerBar`,
`BarButton`, `ZoomPill`, `viewportScript`) encima de cada página, para cambiar el
zoom de maquetación, el modo escritorio y recargar de un toque. Recargar pasa a
hacerse **solo** desde la cabecera: el gesto de arrastrar hacia abajo desaparece.
La cabecera se puede ocultar; oculta, queda una pestaña con un chevron.

Decisiones tomadas contigo:
- **Nada se recuerda entre aperturas**: cada vez que se abre una página empieza
  con la cabecera **oculta** (solo la pestaña del chevron), 100 % y sin
  escritorio. Mientras
  la página sigue abierta (incluida una rotación) sí se mantiene todo.
- **Pestaña del chevron arriba al centro**, colgando bajo la barra de estado.
  Tocarla abre la cabecera; arrastrarla la mueve en horizontal y se queda donde
  se suelte (durante esa apertura).
- **A la izquierda, icono + nombre de la página**, sin botón de volver.
- **Pellizco activado**, además del zoom de la cabecera.

## Diseño

### La cabecera (`web/PageBar.kt`, nuevo)
Portada de `ViewerBar` con el mismo aspecto (Surface `surfaceContainer`, sombra 3dp,
fila de 52dp que pinta también la franja de la barra de estado con
`windowInsetsPadding(safeDrawing Top+Horizontal)`):

`[icono] Nombre de la página …  [escritorio] [− 100% +] [recargar] [^ ocultar]`

- Icono de la página con `PageIcons.bitmap(context, page)` (ya existe; el mismo
  que usan recientes y los accesos directos), ~24dp.
- `BarButton`, `ZoomPill`, `ZoomStep` copiados tal cual; iconos de
  `material-icons-extended` (ya es dependencia): `DesktopWindows`, `Refresh`,
  `KeyboardArrowUp`/`KeyboardArrowDown`. Sin drawables nuevos.
- Barra de progreso fina de 2dp bajo la fila solo mientras carga (1..99), desde
  `WebChromeClient.onProgressChanged`.
- Textos nuevos en `values/strings.xml` y `values-es/strings.xml` (descripciones:
  escritorio activado/desactivado, acercar, alejar, recargar, ocultar cabecera,
  mostrar cabecera).

### La pestaña oculta (en `PageBar.kt`)
- Cuando la cabecera está oculta, la página ocupa todo el alto bajo la barra de
  estado y la pestaña (chevron hacia abajo, esquinas inferiores redondeadas,
  semitransparente) flota encima, pegada al borde superior del área segura.
- Toque → muestra la cabecera. Arrastre horizontal (`detectHorizontalDragGestures`
  con `Modifier.offset`) → la mueve, limitada al ancho de la pantalla; queda donde
  se suelta. Posición en `remember` (no se guarda en ningún sitio).

### Zoom y escritorio (`web/Viewport.kt`, nuevo)
- `viewportScript(base, desktop, zoom)` y las constantes (`DESKTOP_WIDTH = 1280`,
  zoom 30–300 en pasos de 10, pellizco 0.05–10) portadas de claude-history, con su
  explicación: la cabecera re-maqueta (cambia el ancho del viewport), el pellizco
  amplía; se sustituye la meta `viewport` de la página, se fija la escala y 50 ms
  después se libera para devolver el pellizco (cancelando el temporizador previo).
- La cuenta ancho/escala se separa en una función pura
  (`viewportFor(base, desktop, zoom)`) con test JVM en `ViewportTest.kt`
  (100 % = ancho del móvil y escala 1; escritorio = 1280 ajustado; zoom 200 % la
  mitad de ancho; nunca ancho 0).

### `WebActivity`
- Fuera `PullToRefreshLayout`/`OverscrollWebView`: la WebView es una `WebView`
  normal y es lo que se pasa a `PageScreen`.
- Ajustes nuevos: `useWideViewPort`, `loadWithOverviewMode`, `setSupportZoom(true)`,
  `builtInZoomControls = true`, `displayZoomControls = false`.
- Un pequeño contenedor de estado de la ventana (`desktop`, `zoom`, `barHidden`,
  `progress`, como `mutableStateOf`; `barHidden` empieza en `true`) vive en la actividad: el manifiesto ya
  gestiona los cambios de configuración, así que la actividad no se recrea al
  rotar y basta con eso. Cada cambio de modo aplica `viewportScript` al momento
  (sin recargar); `onPageFinished` lo vuelve a aplicar; y un
  `OnLayoutChangeListener` lo reaplica si cambia el ancho de la WebView (rotación),
  porque el ancho base se mide de la vista.
- Recargar = `webView.reload()`; si falla, `onReceivedError` ya relanza la carrera
  de URLs como hasta ahora.

### `PageScreen`
- Columna: cabecera (si visible y la página ya se ha mostrado) + caja con la WebView
  y las superposiciones actuales (buscando / no responde / no existe), con
  `safeDrawing` para los lados y abajo. Mientras se busca servidor o no responde,
  no hay cabecera: esas pantallas ya tienen sus botones.
- Cabecera oculta: la caja lleva también el inset superior y la pestaña va encima.

### Limpieza
- Borrar `web/PullToRefreshLayout.kt` y la dependencia `swiperefreshlayout`
  (`app/build.gradle.kts`, `gradle/libs.versions.toml`).
- `CLAUDE.md`: el apartado «Pull-to-refresh is decided by Chromium…» se sustituye
  por una nota corta: se quitó a propósito, recargar es solo desde la cabecera, y
  por qué no volver a intentar adivinar el gesto; añadir en «How the pieces fit»
  la cabecera y la separación zoom de maquetación / pellizco.
- README («Full screen, with state kept»): quitar «pulling down from the top
  reloads» y describir la cabecera. Se revisa de nuevo al cerrar la rama.

## Forma de trabajo

- Rama propia `page-header` desde `main`.
- **Commits progresivos** a medida que los cambios sean coherentes y compilen.
  Con cada commit va `page-header-progress.md` (en español), que al principio
  contiene este plan completo y se actualiza en cada commit con el estado de la
  implementación. Se queda en la rama mientras pruebas y solo se borra cuando des
  el visto bueno para fusionar, como último commit justo antes del
  `git merge --no-ff`.
- Commits previstos: (1) plan + progreso; (2) `Viewport.kt` + test; (3) cabecera y
  pestaña, WebView sin pull-to-refresh; (4) limpieza de dependencia y docs.
- No se fusiona ni se publica versión hasta que lo digas.

## Archivos

- Nuevos: `app/src/main/java/es/edgarms/weblauncher/web/PageBar.kt`,
  `web/Viewport.kt`, `app/src/test/.../web/ViewportTest.kt`.
- Cambian: `web/WebActivity.kt`, `web/PageScreen.kt`, `res/values/strings.xml`,
  `res/values-es/strings.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`,
  `CLAUDE.md`, `README.md`.
- Se borra: `web/PullToRefreshLayout.kt`.
- Referencia (solo lectura): `../claude-history-android/app/src/main/java/io/github/ems107/claudehistory/ui/ViewerScreen.kt`.

## Verificación

1. `./gradlew test` y `./gradlew assembleDebug` (JAVA_HOME al JDK 21).
2. En el DT50 (`172.30.172.210:5555`): el móvil tiene la build release, así que
   antes: exportar configuración, `adb push` a `/sdcard/Download`, desinstalar,
   `installDebug`, importar. (Los accesos directos fijados hay que volver a
   fijarlos.) Desbloqueo en un solo comando.
3. Con capturas (`adb exec-out screencap -p` al scratchpad), en Jackery e ItsMyMoney:
   - La cabecera se ve con icono, nombre, escritorio, zoom, recargar, ocultar;
     pinta bajo la barra de estado; barra de progreso al cargar.
   - `+`/`−` re-maquetan sin recargar (se conserva el scroll); 30 % y 300 %
     deshabilitan su botón; escritorio cambia a maquetación ancha.
   - Pellizco amplía y después `+` sigue funcionando.
   - Recargar recarga; arrastrar hacia abajo desde arriba ya no hace nada.
   - Ocultar → pestaña arriba al centro; toque la vuelve a abrir; arrastre la mueve
     a izquierda/derecha y se queda; no se sale de la pantalla.
   - Rotar mantiene zoom/escritorio/cabecera y reajusta el ancho.
   - Abrir una página: empieza con la cabecera oculta y solo la pestaña.
   - Cerrar y reabrir la página: todo vuelve a por defecto (cabecera oculta,
     100 %, sin escritorio, pestaña centrada).
   - Tema oscuro (`cmd uimode night yes`) para ver colores de cabecera y pestaña.
4. Informar y esperar a que lo pruebes.
