# Releases

<!--
Release notes, checklists and lessons learned during shipping.
-->

## Estado de la release 0.0.3 (versionCode 3)

- id: release-0-0-3-status-20260922
- type: integration_note
- status: active
- platform: android
- area: release
- date: 2026-09-22

- Firma de la release: `signingConfigs.release` lee `signing/signing.properties` + `signing/stroopOverload.jks` (ambos en .gitignore). Se carga SIN condición: un checkout limpio o un CI sin ese fichero no pasa la fase de configuración de Gradle.
- `proguard-rules.pro` está vacío aunque minify+shrink están activos en release: hay que verificar con una build de release real que la serialización de Firestore/Functions sigue funcionando.
- Textos legales publicados en softyorch.com (`core/LegalLinks.kt`). La ficha de la tienda y las notas de versión están en `store/` (sin commitear a fecha 2026-07-12).
- Build types: debug, demo (sin anuncios, firma de debug, perfil ASO sembrado), release.
