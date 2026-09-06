from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app/build.gradle.kts"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
APP = ROOT / "app/src/main/java/com/coeric/universalbrowser/UniversalBrowserApp.kt"
MONETIZATION = ROOT / "app/src/main/java/com/coeric/universalbrowser/MonetizationCenter.kt"

# Monetization is now part of the product source tree. This script is deliberately
# validation-only so CI never overwrites working browser code with stale upgrade
# templates.
required = {
    GRADLE: 'com.google.android.gms:play-services-ads:25.4.0',
    MANIFEST: 'com.google.android.gms.ads.APPLICATION_ID',
    STRINGS: 'name="admob_banner_unit_id"',
    APP: 'MobileAds.initialize',
    MONETIZATION: 'AdSize.getLargeAnchoredAdaptiveBannerAdSize',
    MONETIZATION: 'widthPixels / density',
    MONETIZATION: 'loadAd(AdRequest.Builder().build())',
}

for path, needle in required.items():
    text = path.read_text()
    if needle not in text:
        raise SystemExit(f"Monetization integration is incomplete: {path} is missing {needle!r}")

print("Monetization source integration validated; no source files were rewritten by CI.")
