#!/bin/bash
# Быстрая валидация .kt файлов перед коммитом

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

errors=0

for file in $(find app/src/main/java -name "*.kt"); do
    opens=$(grep -o '{' "$file" | wc -l)
    closes=$(grep -o '}' "$file" | wc -l)
    if [ "$opens" -ne "$closes" ]; then
        echo -e "${RED}✗ $file: ${opens} открытых, ${closes} закрытых (diff: $((opens - closes)))${NC}"
        errors=$((errors + 1))
    fi
    
    # Проверка на .show()} в конце (частая ошибка)
    if grep -q '\.show()}$' "$file"; then
        echo -e "${RED}✗ $file: пропущен перенос после .show()}${NC}"
        errors=$((errors + 1))
    fi
done

if [ $errors -eq 0 ]; then
    echo -e "${GREEN}✓ Все .kt файлы валидны (скобки сбалансированы)${NC}"
    exit 0
else
    echo -e "${RED}✗ Найдено $errors проблем${NC}"
    exit 1
fi
