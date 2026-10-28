# Native settings implementation plan

1. Add native settings tokens/local theme and measured header/section geometry. Replace shared glass focus paint with a fixed-size rectangular highlight.
2. Replace side-panel shell with right-anchored 360dp native surface and a shared 0.8 dialog barrier. Theme content before page construction; migrate main settings, app actions and Add to row.
3. Apply measured native list layout, radio/switch/slider geometry, remove compounded page padding and restyle existing previews/buttons. Keep actual app accents in previews and opacity control.
4. Add geometry/focus/accent-isolation tests; run complete app tests/analyzer and an optimized signed build. Inspect and navigate on TV, compare with real native panels, correct evidence-based defects.
5. Recapture changed guide/marketing surfaces, update guide wording, validate/publish docs and the new signed app release. Verify published APK identity/cert/checksums and installation/update on TV.

Current status: implementation, independent code review, 224 Flutter tests (4 existing skips), analysis (no errors/warnings), 20 release-tool tests, optimized production-signed build and live TV comparison complete. Body/header/focus pixel samples match native values. Remote submenu navigation, Select move completion, Right row editing, transparent nested Add-to-row barrier and Android settings handoff verified on .89. All 52 valid icon PNGs match APK bytes; empty RedBull/source ZIP retained locally outside packaged assets. Fourteen guide images and six marketing images refreshed; strict guide build and Chrome rendering verified. Publishing and official-release verification pending.
