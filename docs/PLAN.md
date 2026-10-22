# SubmitHub — 졸업작품 고도화 설계 및 계획

> 과제 제출을 Git처럼 관리하는 웹 서비스.
> 제출할 때마다 버전이 쌓이고, 교수는 GitHub PR 리뷰처럼 코드 줄에 피드백을 남기며,
> 학생이 다음 버전에서 그 피드백을 고쳤는지 시스템이 자동으로 추적한다.

- 기반: `D:\Dev\Workspaces\eclipse_workspace` (Swing 버전, 2026-09)
- 담당: 강서원 (1인)
- 기간: 약 6주

---

## 1. 기존 버전에서 바뀌는 점

| 구분 | 기존 (Swing) | 고도화 (SubmitHub) |
| --- | --- | --- |
| 구조 | Swing 앱이 DB에 직접 접속, 파일은 실행한 PC에 저장 → 한 PC에서만 동작 | Spring Boot 서버 + 웹. 여러 사용자가 브라우저로 동시 사용 |
| 계정 | 샘플 계정만, 비밀번호 평문 | 회원가입, BCrypt, 역할별 권한 (Spring Security) |
| 수업 | 없음 (모든 학생이 모든 과제 열람) | 강의 개설 + 초대 코드로 수강 등록 |
| 제출 | 파일 1개, UTF-8만 | 파일 또는 zip(여러 파일), UTF-8/CP949 자동 인식, 마감·지각 처리 |
| diff | LCS (n×m 메모리), "선택 vs 최신"만, CRLF 차이로 전체 변경 표시 | Myers diff, 임의의 두 버전, 좌우 분할 화면, 공백 무시 옵션 |
| 피드백 | 버전 전체에 글 하나 + 점수 | **줄 단위 코드 리뷰** + **피드백 반영 자동 추적** |
| 부정행위 | 없음 | **Winnowing 기반 표절 검사** (MOSS 방식), 뼈대 코드 제외 |
| 분석 | 없음 | 제출 통계 대시보드 |
| 품질 | 테스트 없음, 빌드 도구 없음 | Maven, JUnit, Git, Docker Compose 배포 |

## 2. 핵심 차별 기능 (발표 포인트)

### 2-1. 줄 단위 코드 리뷰
교수가 diff 화면이나 코드 화면에서 줄(또는 범위)을 선택해 코멘트를 남긴다.
학생은 해당 줄 바로 아래에서 코멘트를 보고 답글을 단다. GitHub PR 리뷰와 같은 경험.

### 2-2. 피드백 반영 자동 추적 ★ 독창 기능
학생이 새 버전을 올리면 이전 버전의 모든 코멘트에 대해 diff 로 줄을 따라가서 판정한다.

| 판정 | 의미 | 화면 표시 |
| --- | --- | --- |
| UNCHANGED | 코멘트 달린 줄이 그대로 (줄 번호만 이동했을 수 있음) | 🔴 미반영 (최신 버전 몇 번째 줄인지 표시) |
| MODIFIED | 그 줄이 수정됨 | 🟢 반영됨 (교수 확인 필요) |
| DELETED | 그 줄이 삭제됨 | ⚪ 삭제됨 |

- 들여쓰기만 바꾼 것은 반영으로 보지 않는다.
- 학생: "남은 피드백 N개" 체크리스트. 교수: "피드백 반영률" 통계.
- 구현: `core.review.LineTracker` (완료, 테스트 7개)

### 2-3. 표절 검사
- 같은 과제의 학생별 최신 제출물을 쌍으로 비교해 유사도 순위를 보여준다.
- 변수명 변경, 주석 추가, 들여쓰기·줄바꿈 변경, 문자열 값 변경으로는 피할 수 없다 (구조 토큰 비교).
- 교수가 나눠준 뼈대 코드는 과제 등록 시 올려두면 비교에서 제외된다.
- 의심 쌍을 클릭하면 두 코드를 나란히 놓고 겹치는 줄을 하이라이트한다.
- 구현: `core.similarity` (완료, 테스트 6개)
- 근거 논문: Schleimer et al., "Winnowing: Local Algorithms for Document Fingerprinting", SIGMOD 2003

### 2-4. 제출 통계 대시보드 (교수)
- 과제별 제출률, 미제출자 명단, 지각 제출
- 마감 직전 24시간 제출 비율, 시간대별 제출 분포
- 학생별 버전 수, 피드백 반영률
- Chart.js

## 3. 기술 스택

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| 언어 | Java 17 | PC에 설치된 JDK 17 (`D:\Dev\Tools\java\jdk-17.0.12`) |
| 프레임워크 | Spring Boot 3.5.x | 전자정부프레임워크 경험(Spring)을 그대로 활용 |
| 빌드 | Maven | egov-practice 와 같은 방식 |
| 보안 | Spring Security (폼 로그인, BCrypt) | |
| DB 접근 | Spring Data JPA | |
| DB | MariaDB (운영), H2 (테스트) | 기존 스키마 설계 재사용 |
| 화면 | Thymeleaf + Bootstrap 5 + 약간의 JS | 1인 개발이라 서버 렌더링이 효율적 |
| 코드 하이라이트 | highlight.js | |
| 차트 | Chart.js | |
| 배포 | Docker Compose (app + mariadb) | 시연 PC에서 명령 한 줄로 실행 |

## 4. 패키지 구조

```
submithub/
├── pom.xml
├── docker-compose.yml
├── docs/PLAN.md
└── src/main/java/com/submithub/
    ├── core/            ★ 완료 — 순수 Java 알고리즘 (Spring 의존 없음)
    │   ├── text/        TextDecoder(UTF-8/CP949), LineSplitter(CRLF/LF)
    │   ├── diff/        MyersDiff, SideBySide, DiffOptions
    │   ├── review/      LineTracker (피드백 반영 추적)
    │   └── similarity/  CodeTokenizer, Winnowing, SimilarityChecker
    ├── domain/          JPA 엔티티 + Repository
    ├── service/         비즈니스 로직 (트랜잭션 경계)
    ├── web/             Controller, DTO, 폼 검증
    ├── security/        SecurityConfig, 권한 검사
    └── storage/         FileStorage (서버 디스크 저장)
```

`core` 는 Spring 을 모르는 순수 Java 라서 단위 테스트가 빠르고, 발표 때 "알고리즘을 직접 구현했다"는 근거가 된다.

## 5. DB 설계 (초안)

```
users            id, login_id(UQ), password_hash, name, role(PROFESSOR/STUDENT), student_no, department, created_at
courses          id, professor_id(FK), name, semester, invite_code(UQ), created_at
enrollments      id, course_id(FK), student_id(FK), joined_at, UQ(course_id, student_id)
assignments      id, course_id(FK), title, description, due_at, allow_late(bool), late_until,
                 baseline_code(TEXT, 표절 검사 제외용), created_at
submissions      id, assignment_id(FK), student_id(FK), version_no, message(제출 메모, 커밋 메시지 같은 것),
                 late(bool), submitted_at, UQ(assignment_id, student_id, version_no)
submission_files id, submission_id(FK), path(zip 안 경로), size, sha256, charset,
                 content_text(LONGTEXT, 텍스트일 때), storage_path(원본 파일)
review_comments  id, submission_file_id(FK), author_id(FK), start_line, end_line, body,
                 parent_id(FK, 답글), resolved_by_student(bool), created_at
grades           id, submission_id(FK), professor_id(FK), score(0~100), summary, created_at
similarity_runs  id, assignment_id(FK), threshold, created_at
similarity_pairs id, run_id(FK), submission_a_id, submission_b_id, score, coverage_a, coverage_b
```

- 반영 추적 결과는 저장하지 않고 조회할 때 계산한다 (버전 텍스트가 바뀌지 않으므로 캐시만 둠).
- 같은 파일 내용은 sha256 으로 비교해서 diff 를 건너뛸 수 있다.
- 제출은 INSERT 만 한다. 기존 버전 UPDATE/DELETE 금지 (기존 설계 원칙 유지).

## 6. 기존 버그 → 고도화에서 해결 방법

| 기존 문제 | 해결 |
| --- | --- |
| 버전 번호 조회와 INSERT 가 분리돼 연타 시 유니크 키 오류 | 한 트랜잭션 안에서 `SELECT ... FOR UPDATE`(비관적 락) 또는 유니크 충돌 시 재시도 1회 |
| 점수 칸에 문자 입력 시 예외, 범위 검사 없음 | DTO `@Min(0) @Max(100)` 검증 + 폼 오류 메시지 |
| DB 오류 시 앱이 조용히 멈춤 | `@ControllerAdvice` 공통 오류 페이지 |
| LCS 메모리 폭증 | Myers diff (완료) |
| CRLF/LF 차이로 전체 변경 | LineSplitter (완료) |
| CP949 한글 깨짐 | TextDecoder (완료) |
| 마감 후 제출 제한 없음 | 마감 이후: 지각 허용이면 `late=true`, 아니면 거부 |
| 학생이 남의 제출물 URL 로 접근 가능해질 위험 | 모든 조회에 "본인 제출물이거나 해당 강의 교수" 권한 검사 (IDOR 방지) |
| 업로드 파일 이름으로 경로 조작 (`../`) | 저장 파일명은 UUID, zip 내부 경로 정규화 검사 (Zip Slip 방지) |

## 7. 단계별 계획

각 단계 끝에 테스트 통과 + 커밋. 단계마다 시연 가능한 상태를 유지한다.

### 1주차 — 기반
- [x] Spring Boot 프로젝트 생성 (Maven, Java 17), `core` 패키지와 테스트 이식, `mvn test` 통과
- [x] Git 저장소 생성 (로컬 완료. GitHub 비공개 저장소 연결은 계정 인증이 필요해 직접 `git remote add` 필요)
- [x] 엔티티 + Repository (users, courses, enrollments, assignments)
- [x] Spring Security: 회원가입(학생/교수), 로그인, BCrypt, 역할별 접근
- [x] 레이아웃 (Thymeleaf + Bootstrap): 상단 메뉴, 오류 페이지

### 2주차 — 강의와 과제
- [x] 교수: 강의 개설, 초대 코드 발급 / 학생: 초대 코드로 수강 등록
- [x] 교수: 과제 등록·수정 (마감, 지각 허용, 뼈대 코드)
- [x] 학생: 내 강의 → 과제 목록 (마감 임박 / 제출 완료 / 지각 표시)

### 3주차 — 제출과 버전
- [x] 제출: 파일 또는 zip, 제출 메모, 트랜잭션 + 버전 번호 동시성 처리
- [x] 서버 디스크 저장 (UUID 파일명, 크기 제한, Zip Slip 방지, 바이너리 판별)
- [x] 버전 이력 화면 (타임라인), 파일 트리, 코드 보기 (highlight.js)
- [x] 교수: 과제별 제출 현황 (학생별 최신 버전, 미제출자)

### 4주차 — diff 와 코드 리뷰
- [x] 임의의 두 버전 선택 → 파일별 diff (통합 보기 / 좌우 분할, 공백 무시 토글, 긴 구간 접기)
- [x] 줄(범위) 선택 → 코멘트 작성, 답글
- [x] 버전 단위 점수·총평 (grades)

### 5주차 — 반영 추적, 표절 검사
- [x] 새 버전 제출 시 이전 코멘트 반영 상태 표시 (LineTracker)
- [x] 학생 "남은 피드백" 목록, 교수 반영률
- [x] 표절 검사 실행 → 유사도 순위 → 쌍 비교 화면 (겹치는 줄 하이라이트)

### 6주차 — 통계, 배포, 마무리
- [x] 교수 통계 대시보드 (Chart.js)
- [x] 시연용 샘플 데이터 (교수 1, 학생 6~8, 과제 2, 버전·코멘트·표절 의심 쌍 포함)
- [x] Docker Compose 로 실행(구성 완료, 이 PC 에는 Docker 가 없어 실행은 미검증), README (실행 방법, 화면, 아키텍처, ERD)
- [x] 서비스·권한 통합 테스트 보강

### 여유가 있으면 (선택)
- 코드 자동 채점: 교수가 테스트 입력/기대 출력 등록 → Docker 격리 컨테이너에서 실행. 보안상 반드시 격리, 시간이 부족하면 빼도 됨.
- AI 피드백 초안: diff 를 LLM 에 보내 리뷰 초안 생성, 교수가 수정 후 게시.
- 제출 알림 메일.

## 8. 테스트 방침

- `core`: 알고리즘 단위 테스트 (완료 28개. 무작위 입력 1,000건으로 diff 정확성·최소성 검증 포함)
- 서비스: 버전 번호 동시 제출, 마감/지각 판정, 권한 검사(다른 학생 제출물 접근 차단)를 `@SpringBootTest` + H2 로 검증
- 화면: 주요 흐름만 수동 확인

## 9. 발표 구성 아이디어

1. 문제: 과제를 덮어써서 제출 → 무엇이 바뀌었는지, 피드백을 고쳤는지 알 수 없음. 표절 확인도 수작업.
2. 해결: Git 의 버전·diff·리뷰 개념을 과제 제출에 적용
3. 시연: 학생 v1 제출 → 교수 줄 코멘트 → 학생 v2 제출 → 반영 상태 자동 표시 → 표절 의심 쌍 확인 → 통계
4. 기술: Myers diff / LineTracker / Winnowing 직접 구현, 성능 비교 (LCS 대비 메모리)
5. 한계와 향후 과제
