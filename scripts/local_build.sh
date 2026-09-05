#!/bin/bash
# Локальная сборка APK без GitHub Actions

echo "🔨 Собираем debug APK..."
./gradlew assembleDebug --no-daemon --console=plain

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Сборка успешна!"
    echo "📦 APK: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "Установить на устройство:"
    echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "Или передать по Wi-Fi:"
    echo "  python3 -m http.server 8000"
    echo "  (открой на телефоне http://<ip>:8000/app/build/outputs/apk/debug/app-debug.apk)"
else
    echo ""
    echo "❌ Сборка не удалась. Проверь ошибки выше."
    exit 1
fi
