# Plan: WebAppLauncher — un lanzador Android para mis apps web

> **Estado: fases 0 y 1 hechas.** Esqueleto Gradle que compila e instala; la
> lista de páginas y el editor funcionan en el móvil, y la configuración se
> guarda en `filesDir/config.json`. Siguiente: fase 2 (abrir páginas). Este
> fichero se va actualizando en cada commit con el estado real, y se borra en el
> último commit de la rama, justo antes del merge.
>
> **Fase 1, lo que hay:**
> - `model/`: `Page`, `Config` (+ `ConfigJson`), `PageUrls.isValid`, `Tile`
>   (inicial y color derivado del nombre, reutilizable para los iconos de la fase 4).
> - `data/`: `ConfigStore` (escritura atómica; un fichero corrupto se aparta como
>   `config.json.bad` y se arranca vacío) y `LastWinnerStore` (URL ganadora por
>   página, en `winners.json`, fuera de lo exportable).
> - `ui/`: lista (vacía con mensaje, tocar una página la edita; en la fase 2 pasará
>   a abrirla) y editor (nombre, direcciones con subir/bajar/quitar/añadir,
>   validación `http(s)://`, borrar con confirmación). Textos en inglés y español.
>   Tema azul propio para Android < 12; en 12+ usa los colores dinámicos.
> - Tests JVM: 13 (almacenes, validación de URLs, baldosa), todos en verde.
>
> **El móvil de pruebas:** Urovo DT50, Android 9 (API 28), 720×1440. El WebView
> que usa es el de **Chrome 138** (el WebView de sistema, 74, está desactivado),
> así que JavaScript moderno como `?.` y `??` funciona.
>
> **Cambios respecto a lo previsto al implementar:**
> - adb va **por red** (`adb connect`), no por USB.
> - Versiones fijadas: Gradle 9.7.1, AGP 9.4.0 (Kotlin integrado en AGP),
>   Kotlin 2.4.20, Compose BOM 2026.09.00. Las AndroidX y OkHttp actuales exigen
>   **`compileSdk` 37.2**; `targetSdk` sigue en 36.
> - `sdkmanager` ya delega en la nueva Android CLI (`cmdline-tools\latest\bin\android.exe sdk install ...`),
>   que sale con código 9 aunque instale bien.

## Contexto

Edgar tiene apps web personales servidas desde su PC en la red local (la primera
es la del Jackery Explorer 240) y quiere hacer más. Abrirlas desde el móvil hoy
significa un marcador del navegador con barra de direcciones, y Chrome no ofrece
instalarlas como PWA porque `http://` en una IP de LAN no es un origen seguro.

Se estudió la vía «de manual» — dominio propio, certificado wildcard de Let's
Encrypt por DNS-01 y Caddy como proxy inverso — y **se descarta**: resuelve el
problema pero mete infraestructura, renovaciones y conceptos que Edgar no quiere
mantener para lo que realmente busca, que es entrar cómodo desde el móvil.

El enfoque elegido le da la vuelta al problema: **si la app instalada es una app
nativa propia, el criterio de instalación de Chrome deja de existir.** Un
lanzador con N páginas configuradas, cada una abriéndose en un WebView a
pantalla completa y con su propio acceso directo en el escritorio.

Además resuelve algo que ninguna solución de DNS resolvía bien: el PC cambia de
IP según se conecte por cable o por wifi. Cada página lleva **varias URLs** y el
lanzador prueba todas a la vez, quedándose con la que conteste.

**Lo que este enfoque NO da, y se acepta a sabiendas:** sigue siendo `http://`,
o sea contexto inseguro. Nada de notificaciones push, geolocalización, cámara,
Clipboard API ni `crypto.subtle`, y el tráfico va sin cifrar por la LAN. Ninguna
app actual usa eso. El día que haga falta se añade un certificado propio y el
pinning de su CA en el WebView — unas veinte líneas — sin rehacer nada.

**Este repositorio (`JackeryExplorer240`) no se toca.** El plan vive en un
proyecto nuevo.

## Decisiones acordadas

| Punto | Decisión |
| --- | --- |
| Configuración | Editor dentro de la app, más exportar/importar JSON |
| Herramientas | Solo `cmdline-tools` del SDK; Gradle desde la terminal |
| Instalación en pruebas | `adb` por USB |
| Lenguaje / UI | Kotlin + Jetpack Compose (Material 3) |
| Repositorio | `C:\Users\Edgar\Git\WebAppLauncher`, nuevo |
| Rama | `web-launcher`, desde `main` |

## Preparación del entorno

El JDK ya está en `C:\Program Files\Android\openjdk\jdk-21.0.8` (Gradle 8.x y
AGP 8.x lo admiten). Falta el SDK, que se instala sin Android Studio:

1. Descargar `commandlinetools-win` de Google y descomprimir en
   `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`.
2. `sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"` y
   aceptar las licencias.
3. `JAVA_HOME` y `ANDROID_HOME` apuntando donde toca.
4. En el móvil: Opciones de desarrollador → Depuración USB. `adb devices` tiene
   que verlo.

Son descargas grandes; se hacen en la fase 0 y con permiso.

## Qué se construye

Paquete `es.edgarms.weblauncher`, `minSdk 26` (lo exige el anclado de accesos
directos), `targetSdk 36`.

### El modelo

```kotlin
data class Page(
    val id: String,          // estable: los accesos directos lo referencian
    val name: String,
    val urls: List<String>,  // en orden de preferencia
    val iconPath: String?,   // fichero interno; null = inicial generada
)
```

Un único JSON en `filesDir` con `kotlinx.serialization`. Sin Room ni DataStore:
es una lista de diez elementos, y que el fichero *sea* el formato hace que
exportar e importar no cueste nada.

### La carrera entre URLs — la pieza central

`UrlProber` como interfaz, para poder probarla sin red:

- Se lanzan todas las URLs **a la vez** con OkHttp y timeout de conexión corto
  (~1,5 s). Gana la primera que devuelva **cualquier** respuesta HTTP: un 401 o
  un 404 significa que el servidor está ahí, que es lo único que se pregunta.
- Las perdedoras se cancelan.
- La ganadora se recuerda por página y se prueba primero la próxima vez; solo
  se vuelve a correr la carrera si falla.
- Si no contesta ninguna, **pantalla de error honesta**: qué URLs se probaron,
  qué dijo cada una, y un botón de reintentar. Nunca una pantalla en blanco ni
  el dinosaurio de Chrome.

Esto es lo que hace que el cambio de cable a wifi sea invisible.

### El WebView

Los ajustes que deciden si esto funciona o parece roto:

- `javaScriptEnabled` y **`domStorageEnabled`** — el segundo viene desactivado
  por defecto y es el fallo clásico: la app carga, parece ir, y pierde todo el
  estado.
- `CookieManager.setAcceptCookie(true)` y `flush()` al pausar, o se pierde la
  sesión del PIN en cada salida.
- `WebViewClient`: la misma URL base se queda dentro; los enlaces externos
  salen al navegador del sistema.
- Botón atrás: navega el historial y, agotado, vuelve a la lista.
- A pantalla completa, sin barra de direcciones — que era el objetivo.
- `setWebContentsDebuggingEnabled` solo en debug, para poder inspeccionar desde
  `chrome://inspect`.
- `usesCleartextTraffic` en el manifest: desde Android 9 el `http://` está
  bloqueado por defecto y sin esto no carga nada.

Cada app en su puerto es un origen distinto, así que cookies y almacenamiento
quedan aislados entre páginas sin hacer nada.

### Los accesos directos

`ShortcutManagerCompat.requestPinShortcut` con un intent que lleva el `id` de la
página: un icono en el escritorio que abre directamente esa app, sin pasar por
la lista. Es el «instalar» que se buscaba. Más shortcuts dinámicos (mantener
pulsado el icono del lanzador muestra las páginas), que salen casi gratis.

Hay lanzadores que no admiten anclar; se comprueba con
`isRequestPinShortcutSupported` y se dice en vez de fallar en silencio.

### Los iconos

Al guardar una página se intenta sacar el icono de la web: `<link
rel="manifest">` → iconos del manifest, si no `apple-touch-icon`, si no
`/favicon.ico`. Con jsoup, que es pequeño y evita parsear HTML a mano. Si no
hay nada, se genera una baldosa con la inicial y un color derivado del nombre.
Siempre se puede elegir una imagen a mano.

## Fases

Cada fase son uno o varios commits que compilan, con
`web-launcher-progress.md` actualizado al lado. Ese fichero se borra en el
último commit, justo antes del merge.

0. **Entorno y esqueleto.** SDK instalado, repo creado, commit inicial en `main`
   con README y `.gitignore`, rama `web-launcher`, proyecto Gradle que compila
   e instala en el móvil mostrando una lista vacía.
1. **Modelo y configuración.** `Page`, almacenamiento JSON, pantalla de lista y
   editor: añadir, editar, borrar, reordenar URLs.
2. **Abrir páginas.** `UrlProber` con la carrera, actividad del WebView,
   pantalla de error. **Aquí ya sustituye al navegador.**
3. **Accesos directos**, anclados y dinámicos.
4. **Iconos** automáticos y manuales, y los remates: recarga tirando hacia
   abajo, enlaces externos, comportamiento del atrás.
5. **Exportar/importar** JSON y README.

## Verificación

**Automática** — `./gradlew test`, en la JVM y sin móvil:

- La carrera: gana la primera que responde; un 401 cuenta como viva; si todas
  fallan devuelve el detalle de cada una; se prueba primero la recordada.
- El JSON: ida y vuelta sin pérdida; un fichero corrupto no revienta la app.
- El icono: la cadena manifest → apple-touch-icon → favicon → inicial.

**Manual**, en el móvil de Edgar:

1. Añadir la página del Jackery con sus **dos** IPs, abrirla, meter el PIN y
   encender la salida DC.
2. **La prueba que justifica el proyecto:** con la app abierta, cambiar el PC de
   cable a wifi y volver a entrar. Debe abrir igual, sin tocar nada.
3. Apagar el demonio y entrar: debe salir la pantalla de error diciendo qué
   probó, y recuperarse al volver.
4. Anclar el acceso directo, reiniciar el móvil y comprobar que sigue abriendo.
5. Exportar la configuración, borrar datos de la app, importarla y ver que
   vuelve todo.

## Riesgos conocidos

- **Es una app que hay que mantener**: Kotlin, Gradle, una clave de firma, y
  actualizarla cuando Android rompa algo. No lo da gratis nadie.
- **Depurar es peor que en el navegador**: `chrome://inspect` por USB en vez de
  tener DevTools a mano.
- **Sigue siendo HTTP sin cifrar** y sin APIs de contexto seguro, con la ruta de
  salida descrita arriba.
- Las versiones exactas de AGP, Gradle y build-tools se fijan al implementar,
  contra lo que haya publicado entonces.
