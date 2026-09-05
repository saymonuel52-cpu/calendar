#!/usr/bin/env python3
"""
Безопасное применение патчей с проверкой.
Использование: python3 scripts/safe_patch.py <old_pattern> <new_pattern> <file>
"""
import sys
import os

if len(sys.argv) != 4:
    print("Использование: python3 safe_patch.py <old> <new> <file>")
    sys.exit(1)

old, new, filepath = sys.argv[1], sys.argv[2], sys.argv[3]

if not os.path.exists(filepath):
    print(f"✗ Файл не найден: {filepath}")
    sys.exit(1)

with open(filepath, encoding='utf-8') as f:
    content = f.read()

if old not in content:
    print(f"✗ Паттерн не найден в {filepath}")
    print(f"Искали: {old[:100]}...")
    sys.exit(1)

count = content.count(old)
new_content = content.replace(old, new, 1)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(new_content)

print(f"✓ Патч применён ({count} вхождений, заменено 1)")

# Валидация после патча
opens = new_content.count('{')
closes = new_content.count('}')
if opens != closes:
    print(f"⚠ ВНИМАНИЕ: скобки не сбалансированы ({opens} vs {closes})")
    sys.exit(1)
