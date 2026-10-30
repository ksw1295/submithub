# SubmitHub 운영 배포 가이드 (Oracle Cloud ARM64 기준)

이 문서는 SubmitHub를 **Oracle Cloud Infrastructure (OCI) Always Free ARM (AArch64) 가상 머신 1대**에 처음부터 HTTPS 접속까지 완전하게 배포하고 운영하는 단계별 절차를 설명합니다.

---

## 1. 배포 아키텍처 및 보안 개요

```
[인터넷 (Clients)]
       │
       ▼ (80, 443 포트만 공개 / Caddy 자동 HTTPS 발급)
┌──────────────┐
│    Caddy     │ (Let's Encrypt / ZeroSSL 인증서 자동 갱신, 보안 헤더, 압축)
└──────┬───────┘
       │ (내부 브릿지 네트워크 internal)
       ├─────────────────────────────────────┐
       ▼                                     ▼
┌──────────────┐                      ┌──────────────┐
│  app (Spring │                      │   MariaDB    │
│    Boot)     │                      │   (DB 11)    │
└──────┬───────┘                      └──────────────┘
       │ (DOCKER_HOST=tcp://socket-proxy:2375)
       ▼
┌──────────────┐
│ socket-proxy │ (Docker 소켓 게이트웨이 / 위험 API 차단)
└──────┬───────┘
       │ (/var/run/docker.sock:ro 마운트)
       ▼
[호스트 Docker 데몬]
       │ (격리된 일회용 채점 컨테이너 실행)
       ▼
┌──────────────────────────────────────────────┐
│ 채점 컨테이너 (--network none, --read-only)   │
│ - eclipse-temurin:17-jdk                     │
│ - python:3.12-slim                           │
└──────────────────────────────────────────────┘
```

- **외부 노출 포트**: 오직 Caddy의 HTTP(80), HTTPS(443)와 관리용 SSH(22)만 허용합니다. `app`(8080), `db`(3306), `socket-proxy`(2375)는 외부 포트 바인딩 없이 Docker 내부 네트워크(`internal`)에만 격리됩니다.
- **자동 채점 컨테이너 보안**: `app`에 도커 소켓을 직접 마운트하지 않고, `socket-proxy`를 통해 컨테이너 실행/삭제에 필요한 API만 최소 허용(`CONTAINERS=1`, `POST=1`)하고 이미지·볼륨·네트워크·시스템 API 는 차단합니다. 다만 프록시는 요청 내용까지 걸러내지 못하므로, `app` 이 침해되면 임의 옵션(호스트 폴더 마운트 등)으로 컨테이너를 만들 수 있습니다. 즉 `app` 은 호스트 root 와 가까운 권한을 간접적으로 가진다고 보고, 운영 서버는 채점 전용(다른 중요 데이터 없음)으로 쓰세요. 채점 작업 폴더는 호스트와 `app` 컨테이너의 경로가 같아야 해서 `GRADING_WORK_DIR`(기본 `/srv/submithub/grading-work`)을 같은 경로로 bind mount 합니다(`grading-init` 이 권한을 준비).
- **ARM64 지원**: 배포되는 모든 이미지(`caddy:2-alpine`, `mariadb:11`, `tecnativa/docker-socket-proxy`, `eclipse-temurin:17-jdk`, `python:3.12-slim` 등)는 `linux/arm64`를 공식 지원합니다.

---

## 2. 1단계: OCI 인스턴스 생성 및 네트워크 방화벽 설정

### 2-1. OCI Always Free 인스턴스 사양
- **Image**: Canonical Ubuntu 22.04 또는 24.04 (Minimal 또는 Standard aarch64)
- **Shape**: `VM.Standard.A1.Flex` (Ampere ARM, 2~4 OCPU, 12~24GB RAM 권장)

### 2-2. OCI 콘솔 수신 규칙 (Ingress Rules) 설정
OCI 웹 콘솔에서 인스턴스가 속한 **VCN(가상 클라우드 네트워크) → 보안 목록(Default Security List)**으로 이동하여 아래 3개 포트만 수신 규칙(Ingress Rule)에 추가합니다:

| CIDR | IP 프로토콜 | 대상 포트 범위 | 설명 |
|---|---|---|---|
| `0.0.0.0/0` | TCP | `22` | 관리자 SSH 접속 |
| `0.0.0.0/0` | TCP | `80` | Caddy HTTP (ACME 챌린지 및 HTTPS 리다이렉트) |
| `0.0.0.0/0` | TCP | `443` | Caddy HTTPS (웹 서비스 암호화 통신) |

> **경고**: `8080`, `3306` 포트는 OCI 수신 규칙에 절대로 추가하지 마십시오.

### 2-3. 서버 내부 OS 방화벽 (iptables) 해제 (필수)
Oracle Cloud 우분투 이미지는 **OS 자체 iptables 방화벽에서 22번 포트 외의 모든 수신 패킷을 기본 차단(REJECT)**하도록 설정되어 있습니다. OCI 콘솔에서 80, 443을 열었더라도 OS 수준에서 허용하지 않으면 접속이 되지 않습니다.

SSH 접속 후 서버 터미널에서 다음 명령을 실행합니다:

```bash
# 80(HTTP), 443(HTTPS) 포트 허용 규칙을 REJECT 규칙보다 앞에 삽입
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT

# 재부팅 후에도 규칙이 유지되도록 영구 저장
sudo apt-get update && sudo apt-get install -y iptables-persistent netfilter-persistent
sudo netfilter-persistent save
```

---

## 3. 2단계: 무료 도메인 (DuckDNS) 발급 및 IP 자동 갱신

Caddy의 자동 Let's Encrypt SSL/TLS 발급을 위해 고정 도메인이 필요합니다. 무료 DDNS 서비스인 DuckDNS를 활용합니다.

1. [DuckDNS 홈페이지](https://www.duckdns.org)에 접속하여 로그인합니다.
2. 서브도메인을 등록합니다 (예: `submithub.duckdns.org`).
3. OCI 인스턴스의 공용 IP(Public IP)를 입력하고 저장합니다.
4. OCI 인스턴스 IP가 변경되더라도 자동으로 도메인과 동기화되도록 서버에 갱신 스크립트와 cron을 등록합니다:

```bash
mkdir -p ~/duckdns
cat << 'EOF' > ~/duckdns/duck.sh
#!/bin/bash
DOMAIN="your-domain" # 예: submithub (duckdns.org 앞부분)
TOKEN="your-duckdns-token"
echo url="https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=" | curl -k -s -o ~/duckdns/duck.log -K -
EOF

chmod 700 ~/duckdns/duck.sh
~/duckdns/duck.sh # 첫 갱신 테스트 (duck.log 에 OK 출력 확인)
```

매 10분마다 IP를 자동 갱신하도록 crontab에 등록:
```bash
crontab -e
# 맨 아래에 다음 줄 추가:
*/10 * * * * /home/ubuntu/duckdns/duck.sh >/dev/null 2>&1
```

---

## 4. 3단계: Docker 설치 및 채점 이미지 사전 다운로드

### 4-1. Docker CE 공식 설치
```bash
# Docker 공식 설치 스크립트 실행
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 현재 사용자를 docker 그룹에 추가하여 sudo 없이 실행
sudo usermod -aG docker $USER
sudo systemctl enable docker
sudo systemctl start docker

# 그룹 권한 즉시 반영 (또는 SSH 재접속)
newgrp docker
```

### 4-2. 자동 채점 이미지 사전 다운로드 (Pre-pull)
학생이 코드를 최초 제출할 때 실시간으로 이미지를 받으면 3초 기동 제한 시간을 초과하여 채점 실패가 발생합니다. **서버 시작 전 반드시 미리 받아두어야 합니다**:

```bash
docker pull eclipse-temurin:17-jdk
docker pull python:3.12-slim
```

---

## 5. 4단계: 프로젝트 설정 및 서비스 기동

### 5-1. Git 복제 및 `.env` 설정
```bash
git clone https://github.com/your-username/submithub.git
cd submithub

# 환경변수 템플릿 복사 및 권한 제한
cp .env.example .env
chmod 600 .env
nano .env
```

`.env` 파일에 실제 운영 값을 입력합니다:
```dotenv
DOMAIN=submithub.duckdns.org
DB_USER=submithub
DB_PASSWORD=강력한_랜덤_비밀번호_입력!@#123
DB_ROOT_PASSWORD=강력한_루트_비밀번호_입력!@#456
PROFESSOR_SIGNUP_CODE=교수_가입_인증코드_지정 (비워둘 시 prod 시작 실패)
GEMINI_API_KEY=구글_제미나이_API키 (선택)
DEMO_DATA=false
RATE_LIMIT_LOGIN=10
RATE_LIMIT_SUBMIT=5
RATE_LIMIT_AI_JUDGE=3
AI_DAILY_LIMIT=100
DEMO_RESET_CRON=
```

### 5-2. 문법 확인 및 백그라운드 기동
```bash
# docker compose 문법 및 변수 바인딩 확인
docker compose -f docker-compose.prod.yml config

# 서비스 빌드 및 백그라운드 시작
docker compose -f docker-compose.prod.yml up -d --build
```

### 5-3. 기동 상태 및 Caddy HTTPS 발급 확인
```bash
# 전체 컨테이너 상태 점검 (모두 Up/healthy 상태 확인)
docker compose -f docker-compose.prod.yml ps

# Caddy 로그에서 Let's Encrypt 인증서 발급 확인
docker compose -f docker-compose.prod.yml logs -f caddy
```

웹 브라우저에서 `https://submithub.duckdns.org` 로 접속하여 정상적으로 자물쇠(HTTPS)가 걸려 있는지 확인합니다.

---

## 6. 5단계: 모니터링 (UptimeRobot) 설정

외부에서 서버 장애를 감지할 수 있도록 무료 모니터링 도구인 **UptimeRobot**을 설정합니다.

1. [UptimeRobot](https://uptimerobot.com)에 무료 회원가입 후 로그인합니다.
2. **Add New Monitor** 클릭:
   - **Monitor Type**: `HTTP(s)`
   - **Friendly Name**: `SubmitHub Prod Health`
   - **URL (or IP)**: `https://submithub.duckdns.org/actuator/health`
   - **Monitoring Interval**: `5 minutes`
3. 알림 수단 설정:
   - 장애 발생 시 이메일 또는 텔레그램 봇으로 즉시 알림 수신.
4. **보안 확인**: `/actuator/health` 엔드포인트는 `show-details: never`로 설정되어 있어 내부 DB 정보나 스택 트레이스를 일절 노출하지 않고 오직 `{"status":"UP"}`만 반환하므로 안전합니다.

---

## 7. 6단계: 무중단 업데이트 및 정기 백업

### 7-1. 소스 코드 업데이트 배포
코드 수정 사항을 반영할 때는 DB와 Caddy를 중단하지 않고 앱 컨테이너만 재빌드하여 배포합니다:

```bash
cd ~/submithub
git pull origin main
export GIT_COMMIT=$(git rev-parse HEAD)   # 이미지에 커밋 해시를 기록
docker compose -f docker-compose.prod.yml build app
docker compose -f docker-compose.prod.yml up -d --no-deps app
```

**배포된 코드가 GitHub `main` 과 같은지 확인** (서버에서):

```bash
git -C ~/submithub rev-parse HEAD                      # 서버 작업 폴더의 커밋
git -C ~/submithub ls-remote origin refs/heads/main        # GitHub main 의 커밋 (위와 같아야 함)
docker inspect --format '{{index .Config.Labels "git.commit"}}' $(docker compose -f docker-compose.prod.yml ps -q app)   # 실제 실행 중인 이미지의 커밋
```

세 값이 모두 같으면 배포 완료입니다. 서버 폴더가 `git clone` 으로 받은 것이 아니라(예: zip·scp 로 올림) `git rev-parse` 가 실패하면, 현재 폴더를 `mv ~/submithub ~/submithub.old` 로 치우고 `git clone https://github.com/ksw1295/submithub.git ~/submithub` 로 새로 받은 뒤, 기존 `.env` 와 `logs/` 를 `cp ~/submithub.old/.env ~/submithub/` 처럼 옮기고 위 업데이트 명령을 실행하세요(DB·업로드는 Docker 볼륨에 있어 그대로 유지됩니다). 이후에는 항상 `git pull` 로만 갱신합니다. `GIT_COMMIT` 없이 빌드한 이미지는 라벨이 `unknown` 으로 나오므로 다시 빌드하세요.

### 7-2. DB 및 업로드 파일 정기 백업 스크립트
데이터 손실에 대비하여 DB 덤프와 업로드 파일을 정기적으로 백업합니다:

```bash
mkdir -p ~/backups ~/scripts
cat << 'EOF' > ~/scripts/backup.sh
#!/bin/bash
set -e

BACKUP_DIR="/home/ubuntu/backups"
DATE=$(date +%Y%m%d_%H%M%S)
PROJECT_DIR="/home/ubuntu/submithub"

mkdir -p "$BACKUP_DIR"

# .env 파일에서 DB 비밀번호 로드
source "$PROJECT_DIR/.env"

# 1. MariaDB 데이터베이스 덤프
docker compose -f "$PROJECT_DIR/docker-compose.prod.yml" exec -T db \
    mariadb-dump -u root -p"$DB_ROOT_PASSWORD" submithub | gzip > "$BACKUP_DIR/db_${DATE}.sql.gz"

# 2. 업로드 파일 백업
docker run --rm \
    -v submithub_uploads:/data:ro \
    -v "$BACKUP_DIR":/backup \
    alpine tar -czf "/backup/uploads_${DATE}.tar.gz" -C /data .

# 3. 14일이 지난 오래된 백업 파일 자동 삭제
find "$BACKUP_DIR" -type f -name "*.gz" -mtime +14 -delete

echo "[$(date)] 백업 완료: db_${DATE}.sql.gz, uploads_${DATE}.tar.gz" >> "$BACKUP_DIR/backup.log"
EOF

chmod 700 ~/scripts/backup.sh
```

매일 새벽 3시에 백업이 실행되도록 cron에 등록:
```bash
crontab -e
# 맨 아래에 추가:
0 3 * * * /home/ubuntu/scripts/backup.sh >/dev/null 2>&1
```

---

## 8. 장애 대응 및 서버 유지 관리 (주의사항)

### 8-1. 서버 재부팅 후 자동 복구 확인
모든 서비스는 `restart: unless-stopped`로 설정되어 있고 docker daemon이 systemd에 등록되어 있으므로, 서버가 재부팅되면 자동으로 모든 컨테이너가 복구됩니다.
- 확인 명령: `sudo reboot` 실행 후 재접속하여 `docker compose -f docker-compose.prod.yml ps` 확인.

### 8-2. 긴급 상황 시 휴대폰(모바일) 조치 방법
서버가 응답하지 않을 때 PC가 없어도 스마트폰으로 조치할 수 있습니다:
1. 스마트폰 브라우저로 [Oracle Cloud Console Mobile](https://cloud.oracle.com)에 로그인 (또는 Oracle Cloud 모바일 앱).
2. **Compute → Instances → SubmitHub 인스턴스** 선택.
3. 인스턴스 상단의 **Reboot(재부팅)** 또는 **Force Reboot(강제 재부팅)** 버튼을 누르면 1~2분 내로 서버와 Docker 서비스가 자동 재시작됩니다.

### 8-3. Oracle Cloud Always Free 유휴 회수(Reclamation) 주의 및 대책
- **회수 조건**: 오라클 클라우드는 7일 연속으로 CPU 사용률 20% 미만, 메모리 사용률 20% 미만, 네트워크 사용률 20% 미만인 Always Free 인스턴스를 "유휴(Idle)" 상태로 간주하고 인스턴스를 회수(정지/삭제)할 수 있습니다.
- **확인 방법**: OCI 콘솔 → Compute → Instances → 인스턴스 클릭 후 하단 **Metrics** 탭에서 주간 평균 CPU/메모리 사용률 그래프를 확인합니다.
- **가장 안전한 방지 대책**:
  1. **계정 업그레이드 (Pay-As-You-Go)**: 신용카드를 등록하여 유료(PAYG) 계정으로 전환하면 Always Free 리소스 한도(ARM 4 OCPU, 24GB RAM, 200GB 디스크) 내에서는 **비용이 0원으로 유지되면서 유휴 인스턴스 회수 대상에서 완전히 영구 제외**됩니다. (강력 권장)
  2. UptimeRobot의 5분 주기 헬스체크 및 크론 작업으로 네트워크 트래픽 및 기본 워크로드를 상시 유지합니다.
