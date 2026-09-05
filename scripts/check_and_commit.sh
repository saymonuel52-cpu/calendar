#!/bin/bash
# Быстрая проверка + коммит одним действием

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

echo "🔍 Проверяем синтаксис..."
bash scripts/validate_kotlin.sh

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Валидация не пройдена. Исправь ошибки перед коммитом.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Валидация пройдена${NC}"
echo ""
echo "📝 Коммитим изменения..."
git add -A
git status

read -p "Продолжить? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    read -p "Сообщение коммита: " msg
    git commit -m "$msg"
    echo "✓ Закоммичено"
else
    echo "✗ Отменено"
fi
