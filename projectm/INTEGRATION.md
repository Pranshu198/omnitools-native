# Track C → Coordinator integration spec (do these 4 things)

Everything in Track C's own areas is built and compile-verified. The
following edits are in files Track C does not own — coordinator applies
them.

## 1. AndroidManifest.xml — add the activity (inside `<application>`)

```xml
<activity
    android:name="com.jackbhai.omnitools.nativ.viz.VizActivity"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:exported="false"
    android:screenOrientation="sensor"
    android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen" />
```

No new permissions: `RECORD_AUDIO` is already declared (line 14) and
already requested at runtime by SongsSection's visualizer toggle;
VizActivity requests it itself if missing (requestCode 41).

## 2. Player hook — ONE button in SongsSection (or the player UI)

Where the existing "Visualizer" chip row is built (SongsSection,
~line 419, next to `vizChip`), add a second chip/button:

```java
TextView milkChip = Glass.text(c, "MilkDrop", 12.5f, Glass.TEXT, false);
styleChipPad(milkChip);           // same padding/bg helper as vizChip
milkChip.setOnClickListener(v -> {
    Sfx.tap();
    act.startActivity(new android.content.Intent(
            act, com.jackbhai.omnitools.nativ.viz.VizActivity.class));
});
vizRow.addView(milkChip);
```

(Exact helper names for chip padding/background: copy vizChip's — it is
built two lines above. The intent + class name above are the contract;
no other wiring exists. VizActivity finds the audio session itself via
`PlaybackService.sharedPlayer()`, exactly like `attachVisualizer()`.)

## 3. proguard-native.pro — add keeps

```
# Track C visualizer: JNI entry points are looked up by name, and
# VizActivity is launched via an explicit Intent (manifest-kept), but
# keep the whole viz package so R8 cannot rename the native methods.
-keep class com.jackbhai.omnitools.nativ.viz.** { *; }
```

## 4. build-native.sh — stage the preset pack into assets

**Why:** `build_once()` wipes `assets/` and re-syncs it from the PWA
dist, so presets cannot live in `assets/` directly. They are staged in
`extra-assets/presets/` (126 .milk + PACK-LICENSE.md + PACK-README.md).
Add this right AFTER the PWA sync block (inside `build_once`, after the
`SKIP_PWA_SYNC` if-block, so pass 2 reuses it the same way):

```bash
    # Track C: MilkDrop starter pack (extra-assets survives the PWA sync)
    mkdir -p assets/presets
    cp extra-assets/presets/*.milk assets/presets/
```

The .so needs NO build-script change: `libomniviz.so` for both ABIs is
already staged in `nativelibs/lib/<abi>/` next to `libomnisec.so`, and
the existing `stage_so nativelibs/lib` step packages it.

## Notes for the coordinator

- LGPL-2.1: projectM is static-linked into libomniviz.so. For a
  sideloaded (non-Play) app this is the standard arrangement; the
  projectM license texts are in `projectm/src/COPYING` +
  `LICENSE.txt`, and the rebuild script (`build-projectm.sh`) +
  exact-tag source are in the repo, which is what LGPL relinking
  rights need in practice.
- Preset pack license: MilkDrop presets were almost never formally
  licensed; the bundled pack is projectM's own default "Cream of the
  Crop" selection, whose `PACK-LICENSE.md` (bundled in
  extra-assets/presets/) states the projectM team's position
  (freely released, treated as public domain, per-author takedown on
  request) and whose README says the repo "is meant to be included in
  any projectM-based applications as a preset pack". 126 presets,
  1.8 MB. If a zero-risk stance is preferred, delete
  `extra-assets/presets/*.milk` — the app still ships with the
  user-folder loader (Downloads/OmniPresets + SAF import) and the
  built-in modes.
- VizActivity is self-contained: no Registry/Menu entry is required
  (the SongsSection chip is the entry), but adding one is harmless.
