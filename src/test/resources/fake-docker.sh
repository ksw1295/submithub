#!/bin/bash
# 테스트용 가짜 docker. 실제 컨테이너를 띄우는 대신 제출 파일(Main.java / main.py) 첫 줄의 "SCENARIO:이름" 으로
# 컨테이너 안에서 일어날 일을 흉내 낸다. 받은 인자는 같은 폴더의 docker.log 에 한 줄씩 남긴다.
LOG="$(dirname "$0")/docker.log"
echo "$*" >> "$LOG"

case "$1" in
  version) echo "99.0.0"; exit 0 ;;
  rm) exit 0 ;;
  run) ;;
  *) echo "unknown command" >&2; exit 2 ;;
esac

work=""
prev=""
for a in "$@"; do
  if [ "$prev" = "-v" ]; then work="${a%:/work:*}"; fi
  prev="$a"
done

scenario=""
for f in Main.java main.py; do
  if [ -f "$work/src/$f" ]; then
    scenario=$(head -1 "$work/src/$f" | sed -n 's/.*SCENARIO:\([a-z0-9]*\).*/\1/p')
    break
  fi
done

# 컴파일 컨테이너
if echo "$*" | grep -q "xargs -0 javac"; then
  case "$scenario" in
    compilefail) echo "Main.java:3: error: ';' expected"; exit 1 ;;
    docker125) echo "docker: Error response from daemon: boom" >&2; exit 125 ;;
    compilesleep) exec sleep 30 ;;
  esac
  exit 0
fi

# 실행 컨테이너
case "$scenario" in
  echo) cat ;;
  upper) tr 'a-z' 'A-Z' ;;
  sleep) exec sleep 30 ;;
  exit137) exit 137 ;;
  fail1) echo "boom" >&2; exit 1 ;;
  flood) exec yes xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx ;;
  docker125) echo "docker: Error response from daemon: boom" >&2; exit 125 ;;
  syntax) echo '  File "main.py", line 1' >&2; echo "SyntaxError: invalid syntax" >&2; exit 1 ;;
  *) echo "ok" ;;
esac
