# SubmitHub 시스템 아키텍처 및 설계 명세서

> **프로젝트명**: SubmitHub (버전 관리 기반 과제 제출 및 자동 피드백 추적 플랫폼)  
> **기술 스택**: Java 17 LTS, Spring Boot 3.5, Spring Security, Spring Data JPA, H2/MariaDB, Docker Engine, Gemini REST API, Thymeleaf, Bootstrap 5.3, Pretendard, JetBrains Mono

---

## 1. 전체 시스템 아키텍처 (System Architecture)

SubmitHub는 프레젠테이션 계층, 비즈니스 및 핵심 알고리즘 계층, 인프라 및 격리 실행 계층으로 명확히 분리된 계층형 아키텍처(Layered Architecture)를 따릅니다.

```mermaid
flowchart TB
    subgraph ClientLayer ["1. 클라이언트 계층 (Client Layer)"]
        Browser["웹 브라우저 (Desktop / Mobile)"]
        UI["모던 UI (Pretendard + JetBrains Mono + app.css)"]
    end

    subgraph PresentationLayer ["2. 프레젠테이션 및 보안 계층 (Presentation & Security)"]
        SecFilter["Spring Security Filter Chain"]
        AuthCtrl["인증 컨트롤러 (AuthController)"]
        CourseCtrl["강의/과제 컨트롤러 (Course / Assignment)"]
        SubCtrl["제출/리뷰 컨트롤러 (Submission / Review)"]
        SimCtrl["표절/통계 컨트롤러 (Similarity / Stats)"]
    end

    subgraph ServiceLayer ["3. 비즈니스 로직 및 접근 제어 계층 (Service & Access)"]
        AccessSvc["접근 제어 엔진 (AccessService - IDOR 방어)"]
        SubSvc["제출 서비스 (SubmissionService)"]
        RevSvc["리뷰/코멘트 서비스 (ReviewService)"]
        DiffSvc["비교 서비스 (DiffService)"]
    end

    subgraph EngineLayer ["4. 핵심 알고리즘 엔진 (Core Algorithm Engines)"]
        MyersDiff["Myers Diff 알고리즘 (코드 변경/이동 추적)"]
        FeedbackEngine["피드백 반영 자동 판정 엔진 (FeedbackService)"]
        Winnowing["Winnowing 알고리즘 (토큰 기반 표절 탐지)"]
        AiJudge["Gemini REST AI 판정 보완 엔진 (AiJudgeService)"]
    end

    subgraph InfraLayer ["5. 인프라 및 격리 샌드박스 (Infra & Sandbox Layer)"]
        H2DB[("H2 / MariaDB (RDBMS)")]
        FileStore["로컬 파일 스토리지 (SHA-256 해시 저장)"]
        DockerRunner["DockerGradingRunner (격리 컨테이너 채점)"]
        GitHubAPI["GitHub API 연동 (커밋 자동 임포트)"]
    end

    Browser --> UI
    UI --> SecFilter
    SecFilter --> AuthCtrl & CourseCtrl & SubCtrl & SimCtrl
    AuthCtrl & CourseCtrl & SubCtrl & SimCtrl --> AccessSvc
    AccessSvc --> SubSvc & RevSvc & DiffSvc
    SubSvc & RevSvc & DiffSvc --> MyersDiff & FeedbackEngine & Winnowing & AiJudge
    SubSvc --> FileStore & H2DB & GitHubAPI
    FeedbackEngine & Winnowing --> H2DB
    SubSvc --> DockerRunner
```

---

## 2. 핵심 데이터 흐름 및 파이프라인

### 2.1. 과제 제출 및 자동 파이프라인
1. 학생이 ZIP 파일 또는 GitHub 저장소 URL을 통해 과제를 제출합니다.
2. `UploadProcessor`가 메모리 상에서 파일 목록을 추출하고 유효성을 검증(Zip Slip, 용량 초과 방어)합니다.
3. 버전 번호가 자동 부여되고, 제출 파일들은 SHA-256 해시 기반의 파일 스토리지 및 DB에 저장됩니다.
4. 백그라운드에서 과제에 설정된 Docker 테스트 케이스 자동 채점이 비동기로 수행됩니다.

```mermaid
sequenceDiagram
    actor Student as 학생 (Student)
    participant Ctrl as SubmissionController
    participant Svc as SubmissionService
    participant UpProc as UploadProcessor
    participant GitAPI as GitHub API
    participant Docker as Docker Sandbox

    Student->>Ctrl: POST /assignments/{id}/submit/github
    Ctrl->>Svc: submitFromGitHub(repoUrl, ref)
    Svc->>GitAPI: 커밋 ZIP 아카이브 다운로드
    GitAPI-->>Svc: ZIP 바이너리 반환
    Svc->>UpProc: 압축 해제 및 파일 트리 정규화
    UpProc-->>Svc: Processed 파일 목록
    Svc->>Svc: 새 버전 번호(v2) 발급 및 DB 저장
    Svc-->>Docker: 비동기 자동 채점 트리거
    Docker-->>Svc: 채점 결과(점수, 통과/실패) 저장
    Ctrl-->>Student: v2 제출 완료 리다이렉트
```

---

### 2.2. 피드백 반영 자동 추적 파이프라인 (핵심 킬러 기능)
교수가 이전 버전(v1)의 특정 코드 줄에 코멘트를 남기면, 학생이 새 버전(v2)을 제출했을 때 **Myers Diff 알고리즘**을 통해 해당 줄이 어떻게 변경되었는지 역추적합니다.

```mermaid
flowchart LR
    C1["v1 코드 줄에 피드백 코멘트 작성"] --> Diff["v1 ↔ v2 Myers Diff 알고리즘 실행"]
    Diff --> Match{"해당 코드 줄의 diff 상태 판정"}
    Match -->|"코드 수정/추가됨"| Revised["반영됨 (REVISED)"]
    Match -->|"코드가 그대로 남아있음"| Pending["미반영 (PENDING)"]
    Match -->|"해당 코드가 삭제됨"| Deleted["삭제됨 (DELETED)"]
    Revised & Pending & Deleted --> Stats["반영률 통계 계산 (예: 100%)"]
```

---

### 2.3. 표절 검사 (Winnowing) 파이프라인
학생 간의 변수명 변경, 공백 삽입, 주석 추가 등의 치환 시도를 원천 무력화하고 코드의 구조적 유사도를 측정합니다.

```mermaid
flowchart TD
    RawCode["학생 제출 소스코드 수집"] --> Tokenize["1. 어휘 토큰화 (변수명, 공백, 주석 정규화)"]
    Tokenize --> FilterBase["2. 교수 제공 뼈대 코드(Baseline) 토큰 제외"]
    FilterBase --> KGram["3. K-gram 해시 생성 (슬라이딩 윈도우)"]
    KGram --> Winnow["4. Winnowing 알고리즘 (대표 Fingerprint 추출)"]
    Winnow --> MatchPairs["5. 학생 쌍(Pair) 간 지문 일치율(Jaccard) 계산"]
    MatchPairs --> Heatmap["6. 표절 의심 쌍 추출 및 좌우 2분할 하이라이트 뷰어"]
```

---

## 3. Docker 샌드박스 보안 격리 구조

학생들이 제출한 코드는 임의의 시스템 명령어 실행, 무한 루프, 포크 폭탄(Fork Bomb), 네트워크 해킹 시도 등의 위험을 내포하고 있습니다. SubmitHub는 다중 보안 플래그를 적용하여 호스트 시스템을 완벽히 보호합니다.

```mermaid
flowchart TB
    subgraph Host ["호스트 환경 (Host Machine)"]
        SubmitHubApp["SubmitHub Spring Boot Server"]
        WorkDir["임시 소스코드 마운트 디렉터리 (호스트)"]
    end

    subgraph Sandbox ["Docker 격리 컨테이너 (Sandbox Container)"]
        direction TB
        Sec1["--network none (외부 인터넷/사내망 완전 차단)"]
        Sec2["--read-only (루트 파일시스템 쓰기 금지)"]
        Sec3["--pids-limit 64 (포크 폭탄 방지)"]
        Sec4["--memory 256m (메모리 고갈 방지)"]
        Sec5["--cap-drop ALL & no-new-privileges (루트 권한 박탈)"]
        Sec6["-v hostWorkDir:/work:ro (제출 코드는 읽기 전용으로만 마운트)"]
    end

    SubmitHubApp -->|컨테이너 기동 및 표준입력 주입| Sandbox
    WorkDir -.->|읽기 전용 마운트| Sandbox
```

---

## 4. 데이터베이스 ERD 구조 (Entity Relationship Diagram)

```mermaid
erDiagram
    User ||--o{ Course : "개설 (교수)"
    User ||--o{ Enrollment : "수강 (학생)"
    Course ||--o{ Enrollment : "수강생 등록"
    Course ||--o{ Assignment : "과제 생성"
    Assignment ||--o{ Submission : "제출"
    User ||--o{ Submission : "제출자"
    Submission ||--o{ SubmissionFile : "포함 파일 목록"
    SubmissionFile ||--o{ ReviewComment : "라인 피드백 코멘트"
    ReviewComment ||--o{ ReviewComment : "대댓글 (Replies)"
    Submission ||--o{ Grade : "점수 및 총평"
    Assignment ||--o{ GradingConfig : "자동 채점 환경 설정"
    Assignment ||--o{ GradingTestCase : "테스트 케이스"
    Assignment ||--o{ SimilarityRun : "표절 검사 실행"
    SimilarityRun ||--o{ SimilarityPair : "표절 의심 쌍 매칭"
```
