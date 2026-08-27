#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

APP_NAME="Where Mock Location"
BUILD_DIR=".build/release"
APP_BUNDLE="build/${APP_NAME}.app"

swift build -c release

rm -rf build
mkdir -p "${APP_BUNDLE}/Contents/MacOS"
mkdir -p "${APP_BUNDLE}/Contents/Resources"

cp "${BUILD_DIR}/WhereMockLocationMac" "${APP_BUNDLE}/Contents/MacOS/WhereMockLocationMac"
cp "Resources/Info.plist" "${APP_BUNDLE}/Contents/Info.plist"

hdiutil create -volname "${APP_NAME}" -srcfolder "${APP_BUNDLE}" -ov -format UDZO "build/${APP_NAME}.dmg"

echo "Built build/${APP_NAME}.dmg"
