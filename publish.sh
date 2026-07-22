#!/usr/bin/env bash
#
# Creativitism — release helper.
# Automates the technical steps of preparing a signed release. Run it yourself:
# it will prompt for the passwords it needs (they are never stored in this repo
# except in keystore.properties, which is git-ignored).
#
#   ./publish.sh            # generate key (if needed), build signed AAB + APK
#   ./publish.sh github     # also: push code + create a GitHub Release with the APK
#
# What it does NOT do (only you can, with your own accounts):
#   - create/pay the Google Play developer account ($25 + ID verification)
#   - accept Google's developer agreement
#   - upload the AAB to the Play Console
#
set -euo pipefail
cd "$(dirname "$0")"

KEYSTORE="creativitism-upload.jks"
ALIAS="creativitism-upload"
PROPS="keystore.properties"

echo "== Creativitism release helper =="

# --- 1. Upload keystore -----------------------------------------------------
if [[ ! -f "$KEYSTORE" ]]; then
  echo
  echo ">> No upload keystore found. Creating $KEYSTORE."
  echo "   keytool will ask for a keystore password and a few certificate details"
  echo "   (name/org/country — they don't have to be real, but keep a record)."
  echo "   IMPORTANT: back up this .jks file and its password. If you lose them you"
  echo "   cannot ship updates under the same key without a Google reset."
  echo
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storetype JKS
else
  echo ">> Using existing keystore: $KEYSTORE"
fi

# --- 2. keystore.properties -------------------------------------------------
if [[ ! -f "$PROPS" ]]; then
  echo
  echo ">> Creating $PROPS (git-ignored). Enter the passwords you set above."
  read -rs -p "   Keystore password: " STORE_PW; echo
  read -rs -p "   Key password (Enter to reuse keystore password): " KEY_PW; echo
  KEY_PW="${KEY_PW:-$STORE_PW}"
  cat > "$PROPS" <<EOF
storeFile=$KEYSTORE
storePassword=$STORE_PW
keyAlias=$ALIAS
keyPassword=$KEY_PW
EOF
  echo "   Wrote $PROPS"
else
  echo ">> Using existing $PROPS"
fi

# --- 3. Build signed release ------------------------------------------------
echo
echo ">> Building signed release (AAB for Play, APK for direct install)…"
./gradlew --console=plain bundleRelease assembleRelease

AAB="app/build/outputs/bundle/release/app-release.aab"
APK="app/build/outputs/apk/release/app-release.apk"
echo
echo ">> Done."
[[ -f "$AAB" ]] && echo "   Play upload:      $AAB"
[[ -f "$APK" ]] && echo "   Direct-install:   $APK"

# --- 4. Optional GitHub Release ---------------------------------------------
if [[ "${1:-}" == "github" ]]; then
  echo
  echo ">> GitHub distribution. Requires gh authenticated as an account with WRITE"
  echo "   access to this repo's origin."
  VER="$(grep -m1 versionName app/build.gradle | sed -E 's/.*"([^"]+)".*/\1/')"
  git push origin "$(git rev-parse --abbrev-ref HEAD)"
  gh release create "v$VER" "$APK" \
    --title "Creativitism $VER" \
    --notes "Sideload build. Enable 'install unknown apps' for your browser/file manager, then open the APK."
  echo "   Release v$VER created."
  echo
  echo "   To host the privacy policy for free, enable GitHub Pages on this repo"
  echo "   (Settings → Pages → deploy from branch: main, folder: /docs), then use:"
  echo "     https://<user>.github.io/creativitism/privacy/"
fi

echo
echo "Next (Play Store — only you can do these):"
echo "  1. play.google.com/console/signup  (\$25 once, ID verification)"
echo "  2. Host docs/privacy/index.html and put the URL in the listing"
echo "  3. Upload $AAB, fill the forms from docs/playstore/*.md"
