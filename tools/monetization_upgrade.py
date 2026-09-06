from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app/build.gradle.kts"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
APP = ROOT / "app/src/main/java/com/coeric/universalbrowser/UniversalBrowserApp.kt"
MAIN = ROOT / "app/src/main/java/com/coeric/universalbrowser/MainActivity.kt"
MONETIZATION = ROOT / "app/src/main/java/com/coeric/universalbrowser/MonetizationCenter.kt"


def replace_once(path, old, new):
    text = path.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one anchor in {path}: {old[:80]!r}; found {count}")
    path.write_text(text.replace(old, new, 1))


gradle = GRADLE.read_text()
if 'com.google.android.gms:play-services-ads:25.4.0' not in gradle:
    gradle = gradle.replace(
        'implementation("org.mozilla.geckoview:geckoview:153.0.20260727124451")',
        'implementation("org.mozilla.geckoview:geckoview:153.0.20260727124451")\n    implementation("com.google.android.gms:play-services-ads:25.4.0")'
    )
    GRADLE.write_text(gradle)

if not STRINGS.exists():
    STRINGS.write_text('''<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <!-- Replace these test IDs with your production AdMob/Ad Manager IDs before release. -->\n    <string name="admob_app_id">ca-app-pub-3940256099942544~3347511713</string>\n    <string name="admob_banner_unit_id">ca-app-pub-3940256099942544/6300978111</string>\n</resources>\n''')
else:
    strings = STRINGS.read_text()
    additions = []
    if 'name="admob_app_id"' not in strings:
        additions.append('    <string name="admob_app_id">ca-app-pub-3940256099942544~3347511713</string>')
    if 'name="admob_banner_unit_id"' not in strings:
        additions.append('    <string name="admob_banner_unit_id">ca-app-pub-3940256099942544/6300978111</string>')
    if additions:
        strings = strings.replace('</resources>', '\n' + '\n'.join(additions) + '\n</resources>')
        STRINGS.write_text(strings)

manifest = MANIFEST.read_text()
if 'com.google.android.gms.ads.APPLICATION_ID' not in manifest:
    manifest = manifest.replace(
        'android:allowBackup="true"\n        android:enableOnBackInvokedCallback="true">',
        'android:allowBackup="true"\n        android:enableOnBackInvokedCallback="true">\n        <meta-data\n            android:name="com.google.android.gms.ads.APPLICATION_ID"\n            android:value="@string/admob_app_id" />'
    )
    MANIFEST.write_text(manifest)

app = APP.read_text()
if 'com.google.android.gms.ads.MobileAds' not in app:
    app = app.replace(
        'import android.app.Application\n',
        'import android.app.Application\nimport com.google.android.gms.ads.MobileAds\n'
    )
    app = app.replace(
        'class UniversalBrowserApp : Application() {',
        'class UniversalBrowserApp : Application() {\n    override fun onCreate() {\n        super.onCreate()\n        MobileAds.initialize(this)\n    }\n'
    )
    APP.write_text(app)

MONETIZATION.write_text('''package com.coeric.universalbrowser\n\nimport android.app.Activity\nimport android.view.ViewGroup\nimport android.widget.FrameLayout\nimport com.google.android.gms.ads.AdRequest\nimport com.google.android.gms.ads.AdSize\nimport com.google.android.gms.ads.AdView\n\n/**\n * Lightweight monetization layer. Test IDs are used by default so development\n * builds never generate accidental production traffic. Replace the resource\n * IDs in res/values/strings.xml before publishing a revenue-generating build.\n */\nobject MonetizationCenter {\n    fun createBanner(activity: Activity): AdView {\n        return AdView(activity).apply {\n            adUnitId = activity.getString(R.string.admob_banner_unit_id)\n            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, activity.resources.displayMetrics.widthPixels))\n            layoutParams = FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.WRAP_CONTENT\n            )\n            loadAd(AdRequest.Builder().build())\n        }\n    }\n}\n''')

main = MAIN.read_text()
if 'private var monetizationBanner' not in main:
    main = main.replace(
        '    private var lastMediaPromptAt = 0L\n',
        '    private var lastMediaPromptAt = 0L\n    private var monetizationBanner: com.google.android.gms.ads.AdView? = null\n'
    )
    main = main.replace(
        '        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))\n',
        '        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))\n        monetizationBanner = MonetizationCenter.createBanner(this)\n        root.addView(monetizationBanner, LinearLayout.LayoutParams(-1, -2))\n'
    )
    main = main.replace(
        '    override fun onBackPressed() {',
        '    override fun onDestroy() {\n        monetizationBanner?.destroy()\n        monetizationBanner = null\n        super.onDestroy()\n    }\n\n    override fun onBackPressed() {'
    )
    MAIN.write_text(main)

print("Monetization upgrade applied: SDK, test-safe banner, lifecycle cleanup and production ID resources are configured.")
