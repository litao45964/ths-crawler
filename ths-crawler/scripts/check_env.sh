#!/bin/bash
echo "=== OS ===" && cat /etc/os-release | head -2
echo "=== Java ===" && java -version 2>&1
echo "=== Maven ===" && mvn -version 2>&1 | head -1
echo "=== Python ===" && python3 --version 2>&1
echo "=== Node ===" && node --version 2>&1
echo "=== MySQL ===" && which mysql 2>/dev/null && mysql --version 2>&1 || echo "NOT INSTALLED"
echo "=== Redis ===" && which redis-server 2>/dev/null && redis-server --version 2>&1 || echo "NOT INSTALLED"
echo "=== Chrome ===" && which google-chrome 2>/dev/null && google-chrome --version 2>&1 || echo "NOT INSTALLED"
echo "=== Disk ===" && df -h / | tail -1
echo "=== Memory ===" && free -h | head -2
