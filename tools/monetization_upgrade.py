from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = [
    (ROOT / "app/build.gradle.kts", 'com.google.android.gms:play-services-ads:25.4.0'),
    (ROOT / "app/src/main/AndroidManifest.xml", 'com.google.android.gms.ads.APPLICATION_ID'),
    (ROOT / "app/src/main/res/values/strings.xml", 'name="admob_banner_unit_id"'),
    (ROOT / "app/src/main/java/com/coeric/universalbrowser/UniversalBrowserApp.kt", 'MobileAds.initialize'),
    (ROOT / "app/src/main/java/com/coeric/universalbrowser/MonetizationCenter.kt", 'AdSize.getLargeAnchoredAdaptiveBannerAdSize'),
    (ROOT / "app/src/main/java/com/coeric/universalbrowser/MonetizationCenter.kt", 'widthPixels / density'),
    (ROOT / "app/src/main/java/com/coeric/universalbrowser/MonetizationCenter.kt", 'loadAd(AdRequest.Builder().build())'),
]

# Validation-only: CI must never overwrite working browser code with a stale
# upgrade template. Production IDs remain intentionally outside this commit.
for path, needle in checks:
    if needle not in path.read_text():
        raise SystemExit(f"Monetization integration is incomplete: {path} is missing {needle!r}")

print("Monetization source integration validated; no source files were rewritten by CI.")
