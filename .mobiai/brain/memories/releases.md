# Releases

<!--
Release notes, checklists and lessons learned during shipping.
-->

## Estado de la release 0.0.3 (versionCode 3)

- id: release-0-0-3-status-20260922
- type: integration_note
- status: deprecated
- platform: android
- area: release
- date: 2026-09-22

> **Deprecated 2026-09-29:** sustituida por "Estado de la release 0.0.3 a 2026-09-29". La firma ya es opcional y las reglas R8 están verificadas (ver decisión "Firma de release opcional y reglas reales de R8"). store/ ya está en git.

- Firma de la release: `signingConfigs.release` lee `signing/signing.properties` + `signing/stroopOverload.jks` (ambos en .gitignore). Se carga SIN condición: un checkout limpio o un CI sin ese fichero no pasa la fase de configuración de Gradle.
- `proguard-rules.pro` está vacío aunque minify+shrink están activos en release: hay que verificar con una build de release real que la serialización de Firestore/Functions sigue funcionando.
- Textos legales publicados en softyorch.com (`core/LegalLinks.kt`). La ficha de la tienda y las notas de versión están en `store/` (sin commitear a fecha 2026-07-12).
- Build types: debug, demo (sin anuncios, firma de debug, perfil ASO sembrado), release.

## Estado de la release 0.0.3 a 2026-09-29

- id: release-0-0-3-status-20260929
- type: integration_note
- status: active
- platform: android
- area: release
- date: 2026-09-29

- Sigue siendo versionCode 3 / versionName 0.0.3. Desde el 2026-09-22 se han fusionado en develop los PR #2 a #7 sin subir la versión: habrá que subir versionCode antes de la próxima subida a Play.
- Firma: signing/signing.properties + .jks siguen en .gitignore, pero se cargan de forma condicional; un checkout limpio y el CI configuran sin ellos.
- R8: minify+shrink activos, con reglas verificadas en una build de release real.
- AdMob: IDs de PROD reales desde el 2026-09-28, leídos de propiedades (PROD_KEY_ID_ADMOB_APP); los flavors que no son prod usan IDs de prueba.
- store/ (listing, notas de versión, script de etiquetado de capturas) ya está en git; el listing y los términos incluyen OVERTIME.
- Build types: debug, demo (sin anuncios, firma de debug, perfil ASO sembrado) y release.

### Files
- app/build.gradle.kts
- store/play-store-listing.txt
- store/release-notes.txt
