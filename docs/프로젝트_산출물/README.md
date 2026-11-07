# SubmitHub 프로젝트 공식 산출물 패키지 (Project Deliverables)

> **프로젝트명**: SubmitHub (버전 관리 기반 과제 제출 및 자동 피드백 추적 플랫폼)  
> **개발자**: 강서원 (충남도립대학교 컴퓨터공학과 1인 풀스택 개발)  
> **프로젝트 기간**: 2026.09 ~ 2026.11 (총 8주)  
> **저장 위치**: `D:\Dev\Docs\submithub\프로젝트_산출물\`

---

## 📑 산출물 문서 목록 및 열람 가이드

본 디렉토리는 소프트웨어 공학 표준 생명주기(SDLC)에 따라 체계적으로 작성된 SubmitHub의 공식 기술 산출물 일체를 보관하고 있습니다.

| 번호 | 산출물 문서명 | 주요 내용 | 추천 열람 대상 |
| :---: | :--- | :--- | :---: |
| **01** | [WBS 및 일정계획서](01_WBS_및_일정계획서.md) | • 전체 8주간 4단계 마일스톤 및 세부 WBS 분할표<br/>• Mermaid 기반 간트 차트(Gantt Chart)<br/>• 1인 개발 역할 책임 매트릭스(RAM) 및 커밋 정책 | 프로젝트 관리자, 심사위원 |
| **02** | [요구사항 정의서 (SRS)](02_요구사항_정의서.md) | • 기능적 요구사항 24종 (인증, 과제, 제출, diff, 피드백, 표절, 채점 등)<br/>• 비기능적 요구사항 8종 (IDOR 방어, 호스트 탈출 방어, OOM 차단 등)<br/>• 요구사항 추적성 매트릭스 (RTM) | 설계자, 개발자, QA |
| **03** | [유스케이스 명세서](03_유스케이스_명세서.md) | • 교수/학생/Docker/AI 4대 액터 정의 및 유스케이스 다이어그램<br/>• 5대 핵심 유스케이스 상세 흐름(Primary, Alternative, Exception) 기술서 | 비즈니스 분석가, 사용자 |
| **04** | [시스템 아키텍처 구상도](04_시스템_아키텍처_구상도.md) | • 개념 구상도(Conceptual Blueprint) 및 5계층 논리 아키텍처<br/>• Docker Socket Proxy 기반 물리 네트워크 및 배포 구성도<br/>• 데이터 흐름도 (DFD Level 0 Context Diagram 및 Level 1 서브시스템) | 시스템 아키텍트, 인프라 엔지니어 |
| **05** | [데이터베이스 설계서 및 ERD](05_데이터베이스_설계서_및_ERD.md) | • 불변 제출 모델링(INSERT-Only) 및 피드백 동적 계산 원칙<br/>• 전체 18개 테이블 물리 ERD 다이어그램 (실제 JPA 엔티티 1:1 매핑)<br/>• 테이블 명세서 (Data Dictionary 전수 정의) | DBA, 백엔드 개발자 |
| **06** | [인터페이스 및 API 명세서](06_인터페이스_및_API_명세서.md) | • 웹 화면 라우팅 테이블 및 내부 액션 API 규격<br/>• GitHub REST API, Docker Socket Proxy, Gemini AI 3대 외부 연동 인터페이스 명세 | 풀스택 개발자, 연동 담당자 |
| **07** | [테스트 계획서 및 결과보고서](07_테스트_계획서_및_결과보고서.md) | • 3단계 피라미드 테스트 전략 (단위 $\to$ 서비스/보안 $\to$ 통합)<br/>• 304개 테스트 케이스 100% Pass 실행 기록표<br/>• JPlag 5.x 비교(F1 0.994) 및 Myers Diff(메모리 99% 절감) 정량 평가 요약 | QA 검증관, 기술 평가관 |

---

## 🛠️ 핵심 기술 스택 및 아키텍처 요약

```
[클라이언트 계층]
• Thymeleaf, Bootstrap 5.3, highlight.js, Chart.js, Pretendard, JetBrains Mono

[서버 & 프레임워크 계층]
• Java 17 LTS, Spring Boot 3.5, Spring Security, Spring Data JPA

[자체 구현 알고리즘 코어 (Spring 의존 0%)]
• Myers Diff (O((N+M)D) SES 대각선 탐색)
• LineTracker (복수 라인 범위 피드백 상태 전이 매트릭스)
• Winnowing (Token Lexer 정규화, SplitMix64 해싱, 뼈대코드 O(1) 필터링, 비대칭 포함도)

[인프라 & 보안 샌드박스 계층]
• MariaDB 11, tecnativa/docker-socket-proxy (최소 권한 격리 중계)
• Docker Engine (격리 채점 샌드박스: --network none, --read-only, --pids-limit 64)
• Caddy v2 (Let's Encrypt 자동 TLS 발급, HSTS, nosniff, DENY, Actuator 403 차단)
• Google Gemini 3.8 Flash REST API (AI 심층 피드백 해결 판정)
```

---

## 🔗 상위 디렉토리 연계 문서

- 📘 [알고리즘 코어 로직 상세 설명서 (`../ALGORITHM_CORE.md`)](../ALGORITHM_CORE.md)
- 📐 [시스템 아키텍처 명세서 (`../ARCHITECTURE.md`)](../ARCHITECTURE.md)
- 📊 [정량 평가 보고서 (`../EVALUATION.md`)](../EVALUATION.md)
- 🚀 [심사위원 5분 시연 가이드 (`../DEMO_GUIDE.md`)](../DEMO_GUIDE.md)
- ☁️ [운영 배포 가이드 (`../DEPLOY.md`)](../DEPLOY.md)
- 💼 [웹 포트폴리오 문서 (`../PORTFOLIO.md`)](../PORTFOLIO.md)
