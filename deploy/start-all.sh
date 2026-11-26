#!/usr/bin/env bash
set -euo pipefail

# =====================================================================
# 洪兴洗车电商服务平台 - Linux 一键启动脚本
# 用法：bash deploy/start-all.sh [可选：指定 .env 文件路径]
# =====================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${1:-$ROOT_DIR/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  echo "[INFO] Loaded environment variables from: $ENV_FILE"
elif [[ -f "$ROOT_DIR/.env.local" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.local"
  set +a
  echo "[INFO] Loaded environment variables from: $ROOT_DIR/.env.local"
else
  echo "[WARN] No .env or .env.local found; falling back to application.yml defaults."
fi

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(command -v java)"
else
  echo "[ERROR] Java 17+ runtime not found. Set JAVA_HOME or install OpenJDK 17." >&2
  exit 1
fi

JVM_XMS="${JVM_XMS:-128m}"
JVM_XMX="${JVM_XMX:-256m}"
JVM_EXTRA_OPTS="${JVM_EXTRA_OPTS:--XX:MaxMetaspaceSize=96m -Xss256k -XX:+UseG1GC}"
JAVA_OPTS="-Xms${JVM_XMS} -Xmx${JVM_XMX} ${JVM_EXTRA_OPTS} -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

LOG_DIR="$ROOT_DIR/logs"
PID_DIR="$LOG_DIR/pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

SERVICES=(
  "user-service:8081"
  "product-service:8082"
  "shop-service:8083"
  "order-service:8084"
  "pay-service:8085"
  "review-service:8086"
  "ai-service:8087"
  "gateway:8080"
)

port_open() {
  local host="${SERVER_ADDRESS:-127.0.0.1}"
  [[ "$host" == "0.0.0.0" ]] && host="127.0.0.1"
  local port="$1"
  (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1
}

echo "[1/2] Starting 8 Spring Cloud services (Java: $JAVA_BIN, Heap: -Xms${JVM_XMS} -Xmx${JVM_XMX})..."
for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"
  port="${entry##*:}"
  jar_path="$ROOT_DIR/$svc/target/${svc}-1.0.0.jar"
  pid_file="$PID_DIR/${svc}.pid"

  if [[ ! -f "$jar_path" ]]; then
    echo "[ERROR] Missing $jar_path. Run 'mvn clean package -DskipTests' first." >&2
    exit 1
  fi

  if [[ -f "$pid_file" ]]; then
    old_pid="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$old_pid" ]] && kill -0 "$old_pid" 2>/dev/null; then
      echo "[SKIP] $svc already running (PID=$old_pid, port=$port)"
      continue
    fi
    rm -f "$pid_file"
  fi

  if port_open "$port"; then
    echo "[SKIP] $svc port $port already in use"
    continue
  fi

  # shellcheck disable=SC2086
  nohup "$JAVA_BIN" $JAVA_OPTS -jar "$jar_path" >"$LOG_DIR/${svc}.log" 2>&1 &
  new_pid=$!
  echo "$new_pid" >"$pid_file"
  echo "[START] $svc (PID=$new_pid, port=$port) -> logs/${svc}.log"
done

echo "[2/2] Waiting for service ports to listen (max 90s)..."
for _ in $(seq 1 45); do
  ready=0
  for entry in "${SERVICES[@]}"; do
    port="${entry##*:}"
    if port_open "$port"; then
      ready=$((ready + 1))
    fi
  done
  if [[ "$ready" -eq "${#SERVICES[@]}" ]]; then
    echo "[OK] All ${#SERVICES[@]}/${#SERVICES[@]} services are listening."
    exit 0
  fi
  sleep 2
done

echo "[WARN] Not all ports became ready within 90s. Check logs/*.log:"
for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"
  port="${entry##*:}"
  if port_open "$port"; then
    echo "  [OK]   $svc :$port"
  else
    echo "  [DOWN] $svc :$port"
  fi
done
