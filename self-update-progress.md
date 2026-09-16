# Plan: auto-actualización desde releases de GitHub

## Contexto

Hoy la app solo se actualiza desde el PC con `./gradlew installDebug` por adb. Queremos:

- Que las releases se publiquen desde el repo con un script que las suba a GitHub.
- Que la app compruebe **cada hora** si hay una versión nueva.
- Que la ofrezca de forma **no invasiva pero siempre visible en la pantalla de lista** del launcher. Nunca dentro de una página abierta (`WebActivity`).
- Que desde la app se pueda **forzar la búsqueda** de actualizaciones.

Decisiones tomadas contigo:

- Repo **público `ems107/WebAppLauncher`**: la app lee la API y descarga sin credenciales.
- Firma con un **keystore propio fuera del repo**. En el DT50 habrá que desinstalar la build de depuración una sola vez.
- Las builds de depuración **no buscan actualizaciones**.
- Las versiones de prueba **0.0.X se quedan publicadas**.
- El aviso es una **tarjeta fija arriba de la lista**, sin notificaciones del sistema.

El modelo es lo que ya funciona en `../claude-history`: `scripts/release.mjs` y la sección "Self-update" de `docs/AI_DISTRIBUTION.md`. Lo copiaremos donde aplique: notas en el tag anotado, `gh release create --notes-from-tag`, lista de releases con `If-None-Match` y descarga que puede reanudarse.

## Rama y commits progresivos

- Rama **`self-update`** desde `main`, creada antes del primer commit.
- Commits progresivos cada vez que un bloque sea coherente y compile. En cada commit también se incluye **`self-update-progress.md`**, que empieza con este plan completo y se actualiza con el estado de la implementación.
- Ese fichero sigue en la rama mientras pruebas. Solo se borra cuando des la palabra de merge: primero reviso `git diff main...self-update`, luego compruebo el README, y después hago un commit final y `git merge --no-ff` con un asunto que describa lo que entra.
- Sin PR. No hago merge por mi cuenta.

## Diseño

### 1. Versión, firma y remoto

- **Versión en `gradle.properties`**: `weblauncher.version=0.0.1`, sustituyendo el `0.1.0` que nunca se publicó.
  - `app/build.gradle.kts` la lee.
  - `versionCode` se calcula como `major*10000 + minor*100 + patch`, así no hay dos números que mantener sincronizados.
- **Firma**: `%USERPROFILE%\.android\weblauncher-release.jks` y, al lado, `weblauncher-release.properties` con la ruta, el alias y las contraseñas.
  - Los genero con `keytool`, del JDK 21.
  - `build.gradle.kts` crea `signingConfigs.release` solo si ese fichero existe. Si no existe, la release sale sin firmar y el script se niega a publicarla.
  - Las **copias de seguridad del keystore** son cosa tuya: sin él no se puede publicar ninguna actualización más. Lo diré en README y CLAUDE.md.
- **BuildConfig**:
  - `UPDATE_REPO = "ems107/WebAppLauncher"`.
  - `UPDATES_ENABLED`: `true` en release y `false` en debug.
- **Remoto**: `gh repo create ems107/WebAppLauncher --public --source . --push`. Antes reviso que nada versionado contenga datos privados: IP y PIN no están en el repo.

### 2. Script de release: `scripts/release.ps1`

PowerShell 5.1 y solo ASCII.

```
.\scripts\release.ps1 -Version X.Y.Z (-NotesFile <ruta> | -Notes "texto") [-DryRun] [-AllowBranch]
```

1. **Comprobaciones previas**:
   - `X.Y.Z` es válida y mayor que la versión actual.
   - Estamos en `main`; `-AllowBranch` permite otra rama, y hace falta para las pruebas en esta rama.
   - El árbol está limpio.
   - El tag no existe ni en local ni en origin.
   - La rama no va por detrás de origin.
   - `gh auth status` está bien.
   - Existe el fichero de firma.
2. Pone `JAVA_HOME` (JDK 21 de CLAUDE.md), escribe la versión en `gradle.properties` y ejecuta `gradlew test assembleRelease`.
3. Comprueba la firma del APK con `apksigner verify --print-certs` de build-tools. Si no está firmado con nuestra clave, aborta.
4. Con `-DryRun`, se para aquí y deshace el cambio de versión.
5. Hace el commit `Release vX.Y.Z`, crea el tag anotado con las notas (`--cleanup=verbatim`) y hace push de la rama y del tag.
6. Ejecuta `gh release create vX.Y.Z weblauncher-X.Y.Z.apk --title vX.Y.Z --notes-from-tag`. Si falla, imprime cómo reintentar o cómo borrar el tag, igual que `release.mjs`.

### 3. Lógica pura, probada en la JVM: paquete `update/`

- **`Version.kt`**: parsea `vX.Y.Z` o `X.Y.Z` y las compara. Lo que no encaje en ese formato se ignora.
- **`ReleaseFeed.kt`**: decodifica la respuesta de `GET /repos/{repo}/releases?per_page=30` con kotlinx.serialization.
  - Descarta borradores, prereleases y releases sin un asset `.apk`.
  - Devuelve la más nueva que sea mayor que la versión actual: versión, notas, URL del APK y tamaño.
- **Tests**: `VersionTest`, `ReleaseFeedTest` (JSON de ejemplo en el test) y `GitHubReleaseSourceTest` con `MockWebServer`, igual que `OkHttpUrlProberTest`.

### 4. Estado y comprobación

- **`GitHubReleaseSource.kt`** (OkHttp):
  - Envía un `User-Agent`, que GitHub exige.
  - Usa `If-None-Match` con el ETag guardado, así una respuesta 304 no gasta cuota. El límite sin autenticar es de 60/h por IP pública.
  - Distingue sin red, límite alcanzado y error del servidor.
- **`UpdateStore.kt`**: `update.json` en `filesDir`, con el mismo patrón que `data/LastWinnerStore.kt`, usando `Files.writeAtomically`. Guarda:
  - la última comprobación,
  - el ETag,
  - la última release disponible (para que la tarjeta aparezca en frío sin red).
- **`UpdateRepository.kt`**: la única copia del estado mientras la app corre. Vive en `WebLauncherApp`, igual que `pages`.
  - Expone un `StateFlow<UpdateState>` con los estados: sin novedad, comprobando, disponible, descargando (progreso), instalando y error.
  - `check(force)`: sin `force` no hace nada si la última comprobación tiene menos de una hora.
  - Al arrancar borra APKs descargados de versiones iguales o anteriores a la instalada.
- **`UpdateCheckWorker.kt`** (WorkManager, dependencia nueva `androidx.work:work-runtime-ktx`):
  - Trabajo periódico de 1 h con la restricción de tener red.
  - Se programa con `enqueueUniquePeriodicWork(KEEP)` en `WebLauncherApp.onCreate`, solo si `UPDATES_ENABLED`.
- **Red de seguridad por si el fabricante mata WorkManager**: cada vez que la pantalla de lista pasa a `RESUMED` llama a `check(force = false)`.

### 5. Descarga e instalación: `ApkInstaller.kt`, `InstallResultReceiver.kt`

- **Descarga** con OkHttp a `cacheDir/updates/weblauncher-X.Y.Z.apk.part`:
  - Se reanuda con `Range` y se reintenta varias veces.
  - El límite de tiempo es por silencio, no por duración total.
  - Al terminar se renombra y se comprueba el tamaño contra el asset.
- **Permiso para instalar**:
  - Si `packageManager.canRequestPackageInstalls()` es false, un diálogo lo explica y abre `ACTION_MANAGE_UNKNOWN_APP_SOURCES` para nuestra app.
  - Al volver se reintenta.
- **Instalación** con una sesión de `PackageInstaller`. El resultado llega a un `BroadcastReceiver` no exportado:
  - `STATUS_PENDING_USER_ACTION`: lanza el intent de confirmación del sistema.
  - Fallo: pasa el estado a error con el mensaje. Por ejemplo, `STATUS_FAILURE_CONFLICT` significa firma distinta.
  - Si todo va bien, Android reinicia la app en la versión nueva.
- **Manifiesto**: `REQUEST_INSTALL_PACKAGES` y el receiver.

### 6. Interfaz (solo `PageListScreen`)

- **`ui/list/UpdateBanner.kt`**: una `Card` tonal arriba de la lista, visible también cuando la lista está vacía. Dice "Versión X.Y.Z disponible" con el botón "Actualizar".
  - Durante la descarga muestra una barra de progreso.
  - Si hay error, lo muestra con "Reintentar".
  - Al pulsarla abre un diálogo con las notas y el botón "Instalar".
  - No se puede descartar: sigue ahí mientras la versión nueva exista.
- **Menú ⋮ de la lista**:
  - Nueva entrada "Buscar actualizaciones", con la versión instalada como texto secundario.
  - Lanza `check(force = true)` y responde con un snackbar: "Ya tienes la última versión (X.Y.Z)", "Sin conexión con GitHub", o simplemente aparece la tarjeta.
  - En debug la entrada no aparece.
- `MainActivity.LauncherNavHost` conecta un `UpdateViewModel` fino, con el mismo patrón que `PagesViewModel`.
- `WebActivity` y `PageScreen` no se tocan.
- Textos nuevos en `values/strings.xml` y `values-es/strings.xml`.

### 7. Documentación

- **CLAUDE.md**:
  - Cómo se publica una release y dónde está el keystore.
  - **Nunca publicar una release sin que Edgar lo pida.**
  - Las builds de depuración no se actualizan solas y tienen otra firma.
  - Trampas nuevas que aparezcan durante las pruebas.
- **README**: la actualización desde la app, cómo publicar una release y la advertencia de guardar el keystore. Lo reviso de nuevo al cerrar el plan.

## Ficheros principales

- **Nuevos**:
  - `scripts/release.ps1`
  - `app/src/main/java/es/edgarms/weblauncher/update/`: `Version`, `ReleaseFeed`, `GitHubReleaseSource`, `UpdateStore`, `UpdateRepository`, `UpdateCheckWorker`, `ApkInstaller`, `InstallResultReceiver`
  - `ui/list/UpdateBanner.kt`
  - `ui/UpdateViewModel.kt`
  - Tests en `app/src/test/.../update/`
- **Modificados**:
  - `gradle.properties`
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `AndroidManifest.xml`
  - `WebLauncherApp.kt`
  - `MainActivity.kt`
  - `ui/list/PageListScreen.kt`
  - `values*/strings.xml`
  - `CLAUDE.md`
  - `README.md`

## Orden de los commits

1. Versión en `gradle.properties`, firma y BuildConfig. Se crea el repo público y se hace push.
2. `Version` y `ReleaseFeed` con sus tests.
3. `GitHubReleaseSource`, `UpdateStore`, `UpdateRepository` y el worker, con sus tests.
4. Descarga e instalación, y el manifiesto.
5. Tarjeta, menú y strings en/es.
6. `scripts/release.ps1`.
7. Documentación y trampas encontradas durante las pruebas.

## Verificación

**En la JVM**: `gradlew test` en verde, incluidos los tests nuevos.

**En el DT50** (`adb connect 172.30.172.210:5555`, PIN 1975 si está bloqueado; capturas en el scratchpad):

1. **Guardar tu configuración antes de desinstalar**: `run-as ... cat files/config.json` (la build actual es debug). El fichero va a `/sdcard/Download` para importarlo después desde la app.
2. **Publicar la 0.0.1**: `release.ps1 -Version 0.0.1 -AllowBranch`. Desinstalar la debug, `adb install` del APK de la release, importar la configuración y comprobar que las páginas y los accesos directos funcionan.
3. **Publicar la 0.0.2** y probar la búsqueda forzada: menú → "Buscar actualizaciones" → aparece la tarjeta → notas → Instalar. Dar el permiso de orígenes desconocidos y confirmar. Luego comprobar:
   - `dumpsys package es.edgarms.weblauncher | grep versionName` dice 0.0.2;
   - la configuración, los iconos y los accesos directos siguen ahí;
   - la tarjeta ya no aparece.
4. **Publicar la 0.0.3** y probar la comprobación horaria sin tocar nada: comprobar en `dumpsys jobscheduler` que el trabajo periódico está programado y forzar su ejecución con `cmd jobscheduler run -f`. La tarjeta debe aparecer al abrir la lista sin haber buscado a mano. Instalar la 0.0.3.
5. **Casos límite**:
   - Con una página abierta no aparece nada.
   - Sin red, la búsqueda forzada lo dice.
   - "Ya tienes la última versión" cuando corresponde.
   - Cancelar la confirmación del sistema deja la tarjeta con "Reintentar".
   - Una segunda comprobación devuelve 304, visible en logcat.

**No se puede probar en el móvil**: que el trabajo horario se dispare solo tras horas con la pantalla apagada. Solo compruebo que está programado y que funciona cuando se fuerza.

## Estado de la implementación

- [x] 1. Versión en `gradle.properties` (0.0.1, `versionCode` derivado), firma de release desde `%USERPROFILE%\.android\weblauncher-release.properties` (keystore generado; APK de release verificado con `apksigner`), `BuildConfig.UPDATE_REPO`/`UPDATES_ENABLED`, dependencia de WorkManager. Repo público creado y `main` subido.
- [ ] 2. `Version` y `ReleaseFeed` con tests.
- [ ] 3. `GitHubReleaseSource`, `UpdateStore`, `UpdateRepository`, worker.
- [ ] 4. Descarga e instalación.
- [ ] 5. Tarjeta, menú y strings.
- [ ] 6. `scripts/release.ps1`.
- [ ] 7. Documentación.
- [ ] Pruebas en el DT50 (0.0.1 → 0.0.2 → 0.0.3).
