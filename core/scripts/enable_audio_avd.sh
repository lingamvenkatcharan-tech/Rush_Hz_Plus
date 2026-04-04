#!/bin/bash
SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_ABS_PATH="$SCRIPT_PATH/$(basename "$0")"

AVD_DIR="$HOME/.android/avd"

echo "🔧 Enabling audio for all AVDs in: $AVD_DIR"
echo "📂 Script absolute path: $SCRIPT_ABS_PATH"
echo "------------------------------------------------------"

for CONFIG in "$AVD_DIR"/*.avd/config.ini; do
  [ -e "$CONFIG" ] || continue  # 파일 없으면 skip
  AVD_NAME=$(basename "$(dirname "$CONFIG")" .avd)
  echo "Updating audio settings for $AVD_NAME ..."

  sed -i '' '/hw.audioInput/d' "$CONFIG" 2>/dev/null
  sed -i '' '/hw.audioOutput/d' "$CONFIG" 2>/dev/null
  sed -i '' '/hw.audioLatency/d' "$CONFIG" 2>/dev/null
  sed -i '' '/hw.audioPlayback/d' "$CONFIG" 2>/dev/null
  sed -i '' '/hw.audioCapture/d' "$CONFIG" 2>/dev/null

  {
    echo "hw.audioInput=yes"
    echo "hw.audioOutput=yes"
    echo "hw.audioLatency=low"
    echo "hw.audioPlayback=yes"
    echo "hw.audioCapture=yes"
  } >> "$CONFIG"

  echo "✅ Audio enabled for: $AVD_NAME"
  echo "   ↳ Config file: $CONFIG"
  echo "------------------------------------------------------"
done

echo "🎉 Audio input/output successfully enabled for all AVDs."
echo "📍 Script absolute path: $SCRIPT_ABS_PATH"
