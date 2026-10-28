# SubmitHub 운영 배포 가이드 (Production Deployment Guide)

이 문서는 SubmitHub를 실서버(Linux ARM64 / AArch64) 환경에 Docker Compose와 Caddy를 사용하여 안전하게 배포하고 운영하기 위한 절차와 아키텍처를 설명합니다.

---

## 1. 아키텍처 및 서비스 구성

```
[인터넷 (Clients)]
       │
       ▼ (80, 443 포트만 공개 / 자동 HTTPS)
┌──────────────┐
│    Caddy     │ (역방향 프록시, Let's Encrypt TLS 인증서 자동 관리)
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
│ socket-proxy │ (Docker API 게이트웨이 / 위험 API 차단)
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

- **외부 공개 포트**: Caddy의 HTTP(80), HTTPS(443)만 외부에 열립니다.
- **내부 포트 격리**: `app`(8080), `db`(3306), `socket-proxy`(2375)는 호스트 포트를 바인딩하지 않으며 Docker 내부 네트워크(`internal`) 안에서만 통신합니다.
- **헬스체크**:
  - `db`: `healthcheck.sh --connect --innodb_initialized` (MariaDB 공식 헬스체크)
  - `app`: `/actuator/health` 엔드포인트를 통해 상태 점검. 외부에 노출 시 `management.endpoint.health.show-details: never` 설정을 통해 `{"status":"UP"}` 외의 내부 구성 정보(DB 연결 정보, 디스크 상태 등)가 노출되지 않도록 보호됩니다.

---

## 2. 자동 채점 컨테이너 실행 방식 비교 및 보안 분석

SubmitHub는 학생이 제출한 Java 및 Python 코드를 호스트가 아닌 격리된 도커 컨테이너에서 컴파일하고 실행합니다.
`app` 컨테이너가 채점 컨테이너를 생성/실행하는 방법 3가지를 비교합니다.

| 방식 | 구성 요약 | 보안 위험 및 한계 | 안전성 평가 |
|---|---|---|---|
| **1. 도커 소켓 직접 마운트**<br>(`/var/run/docker.sock`) | `app` 컨테이너에 호스트 도커 소켓을 그대로 볼륨 마운트 | - **호스트 완전 장악(Root 탈취)**: 도커 소켓 권한은 호스트의 root 권한과 동일함. 만약 웹 앱 취약점(RCE 등)이 발생하면 공격자가 `docker run -v /:/host-root ...` 명령으로 호스트 루트 파일시스템 전체를 장악하고 컨테이너를 탈출할 수 있음.<br>- 타 컨테이너(DB, Caddy) 임의 조작/삭제 가능. | **절대 사용 금지**<br>(가장 위험) |
| **2. 도커 소켓 프록시 분리**<br>(`socket-proxy`) **[채택]** | 소켓은 `socket-proxy` 컨테이너만 읽기 전용(`:ro`)으로 마운트하고, `app`은 격리된 내부망에서 프록시 TCP(`2375`)로 통신 | - 프록시 레벨에서 위험 API 원천 차단: `VOLUMES=0`, `NETWORKS=0`, `EXEC=0`, `SWARM=0`, `IMAGES=0`, `SYSTEM=0`.<br>- 채점에 꼭 필요한 `CONTAINERS=1`, `POST=1`만 화이트리스트로 허용.<br>- `app` 컨테이너 내부에는 소켓 파일 자체가 존재하지 않음.<br>- DooD(Docker-out-of-Docker) 바인드 마운트 경로 매핑 관리 필요. | **가장 안전 (권장)** |
| **3. 호스트 직접 실행**<br>(app을 systemd로 실행) | Caddy와 MariaDB만 컨테이너로 띄우고, Spring Boot 앱은 호스트 OS의 비루트 계정(`submithub`)으로 직접 실행 | - DooD 파일 경로 불일치 문제가 원천 차단됨.<br>- 하지만 호스트에 JDK 17 등 런타임을 직접 설치·관리해야 하여 컨테이너 기반 일관 배포의 이점이 줄어듦.<br>- `docker` 그룹에 속한 사용자는 사실상 root 권한을 가지므로 해당 계정의 보안 관리가 중요함. | **대안 가능**<br>(수동 관리 필요) |

> **선택된 구성**: SubmitHub의 `docker-compose.prod.yml`은 **방법 2(도커 소켓 프록시)**를 채택하여, `app`이 호스트 도커 소켓에 직접 접근하지 못하도록 강력하게 격리합니다.

---

## 3. ARM64 (AArch64) 아키텍처 호환성 검증

배포 대상 서버가 ARM64(aarch64) 아키텍처이므로, 사용되는 모든 도커 이미지가 `linux/arm64`를 공식 지원하는지 검증했습니다.

| 이미지 명칭 | 용도 | ARM64 (aarch64) 지원 여부 | 검증 결과 |
|---|---|:---:|---|
| `caddy:2-alpine` | 역방향 프록시 / 자동 HTTPS | **지원** | Docker 공식 다중 아키텍처 매니페스트 (linux/arm64 지원) |
| `mariadb:11` | 데이터베이스 | **지원** | MariaDB 공식 이미지 (linux/arm64 지원) |
| `tecnativa/docker-socket-proxy:latest` | 도커 소켓 보안 프록시 | **지원** | 공식 다중 아키텍처 매니페스트 (linux/arm64 네이티브 빌드) |
| `eclipse-temurin:17-jdk` | Java 채점용 컨테이너 | **지원** | Adoptium 공식 다중 아키텍처 매니페스트 (linux/arm64 지원) |
| `python:3.12-slim` | Python 채점용 컨테이너 | **지원** | Python 공식 다중 아키텍처 매니페스트 (linux/arm64 지원) |
| `maven:3.9-eclipse-temurin-17` | 앱 빌드 스테이지 | **지원** | Multi-arch 지원 |
| `eclipse-temurin:17-jre` | 앱 실행 런타임 | **지원** | Multi-arch 지원 |
| `docker:cli` | 앱 내부 docker CLI 바이너리 | **지원** | Multi-arch 지원 |

> **결론**: 모든 구성 이미지가 ARM64 아키텍처를 공식 지원하므로 에뮬레이션 없이 네이티브 성능으로 구동됩니다.

---

## 4. 사전 필수 작업: 채점 이미지 다운로드 (Pre-pull)

채점 컨테이너(`eclipse-temurin:17-jdk`, `python:3.12-slim`)는 학생의 코드가 최초 제출될 때 실시간으로 실행됩니다.
만약 서버 기동 후 미리 이미지를 받아두지 않으면, 첫 채점 시 기가바이트 단위의 이미지를 다운로드하느라 채점 타임아웃(3초 기동 유예 시간 초과)이 발생합니다.

따라서 서버를 가동하기 전에 **반드시 호스트 환경에서 아래 명령으로 채점 이미지를 미리 받아두어야 합니다**:

```bash
docker pull eclipse-temurin:17-jdk
docker pull python:3.12-slim
```

---

## 5. 단계별 배포 순서

### 1단계: 환경 변수 설정
`.env.example` 파일을 복사하여 `.env` 파일을 생성하고 실제 운영 값을 입력합니다:

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

필수 입력 값:
- `DOMAIN`: 서비스 도메인 (예: `submithub.your-univ.ac.kr`)
- `DB_USER`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`: 데이터베이스 계정 및 강력한 무작위 비밀번호
- `PROFESSOR_SIGNUP_CODE`: 교수 가입 인증용 보안 코드 (비어 있으면 prod 프로필 시작 실패)
- `GEMINI_API_KEY`: Google Gemini API 키 (선택 사항)

### 2단계: 채점 이미지 사전 다운로드
```bash
docker pull eclipse-temurin:17-jdk
docker pull python:3.12-slim
```

### 3단계: Docker Compose 설정 검증
문법과 환경 변수 바인딩에 문제가 없는지 검증합니다:

```bash
docker compose -f docker-compose.prod.yml config
```

### 4단계: 서비스 빌드 및 백그라운드 시작
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

### 5단계: 상태 및 헬스체크 확인
모든 컨테이너가 `healthy` 상태가 되었는지 확인합니다:

```bash
docker compose -f docker-compose.prod.yml ps
```

외부에서 Caddy 및 애플리케이션 헬스체크가 정상 동작하는지 확인합니다:
```bash
curl -i https://<당신의도메인>/actuator/health
```

정상 응답:
```http
HTTP/2 200
content-type: application/vnd.spring-boot.actuator.v3+json

{"status":"UP"}
```
*(기밀 정보나 스택 트레이스 없이 status 만 정상 노출됨)*
