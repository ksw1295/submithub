# [산출물 05] 데이터베이스 설계서 및 데이터 명세서 (Database Design Specification)

> **프로젝트명**: SubmitHub (버전 관리 기반 과제 제출 및 자동 피드백 추적 플랫폼)  
> **DBMS**: MariaDB 11 LTS (운영) / H2 In-Memory (로컬/테스트)  
> **문서 버전**: v1.3 (전수 소스코드 스키마 팩트체크 완료)  
> **검증 일자**: 2026-11-07  
> **작성자**: 강서원

---

## 1. 데이터베이스 설계 원칙 및 모델링 전략

### 1.1. 불변 제출 이력 모델링 (INSERT-Only Architecture)
- 과제 제출 도메인(`submissions`, `submission_files`)은 Git의 커밋 스냅샷과 동일하게 **불변(Immutable)** 데이터로 취급됩니다 (`@Immutable`).
- 한 번 제출된 버전은 절대 `UPDATE`되거나 물리 삭제(`DELETE`)되지 않으며, 오직 새로운 버전으로 `INSERT`만 발생합니다.
- 복합 고유 키 `uq_submission_version(assignment_id, student_id, version_no)`를 통해 동시 제출 시 중복 채번을 방어합니다.

### 1.2. 조별과제(Team) 모델링 원칙
- 팀은 강의가 아닌 **과제 단위(`Assignment`)**에 바인딩됩니다 (`Team.assignment_id`).
- 팀장은 `Team.leader_id`(`User` FK)로 식별하며, 팀원(`TeamMember`)은 담당 **Git 브랜치명(`branch_name`)**과 **역할 타이틀(`role_title`)**을 가집니다.
- `team_members`는 `(assignment_id, student_id)` 복합 고유키를 두어 학생이 한 과제 내에서 여러 팀에 중복 가입하는 것을 방지합니다.

### 1.3. 피드백 반영 상태의 동적 계산 원칙
- 줄 피드백(`review_comments`)의 반영 상태(🔴미반영/🟢반영됨/⚪삭제됨)는 DB 컬럼에 고정 저장하지 않습니다.
- 최신 버전과의 관계에 따라 상태가 지속적으로 변하므로, 조회 시점마다 `LineTracker` 알고리즘이 Myers diff를 통해 메모리 상에서 동적으로 계산하여 데이터 불일치(Inconsistency)를 원천 방지합니다.

---

## 2. 전체 물리 ERD (Entity Relationship Diagram - 실제 JPA 엔티티 기반)

```mermaid
erDiagram
    users ||--o{ courses : "개설 (교수)"
    users ||--o{ enrollments : "수강 신청 (학생)"
    courses ||--o{ enrollments : "수강생"
    courses ||--o{ assignments : "과제"
    
    assignments ||--o{ teams : "과제 팀 구성"
    users ||--o{ teams : "팀장 (leader)"
    teams ||--o{ team_members : "소속 팀원"
    assignments ||--o{ team_members : "과제 기준 팀원"
    users ||--o{ team_members : "소속 학생"

    assignments ||--o{ submissions : "제출 이력"
    users ||--o{ submissions : "제출자 (student)"
    submissions ||--o{ submission_files : "제출 파일 목록"
    submissions ||--o| grades : "점수 및 총평"
    users ||--o{ grades : "채점 교수"

    submission_files ||--o{ review_comments : "줄 단위 코멘트"
    users ||--o{ review_comments : "코멘트 작성자"
    review_comments ||--o{ review_comments : "답글 (Parent-Child)"

    assignments ||--o| grading_configs : "자동 채점 환경"
    assignments ||--o{ grading_test_cases : "테스트 케이스"
    submissions ||--o| grading_runs : "채점 실행 기록"
    grading_runs ||--o{ grading_results : "케이스별 결과"

    assignments ||--o{ similarity_runs : "표절 검사 실행"
    similarity_runs ||--o{ similarity_pairs : "표절 의심 쌍"

    review_comments ||--o{ ai_judgements : "AI 심층 판정"
    ai_daily_usage {
        date usage_date PK
        int call_count
    }
```

---

## 3. 테이블 명세서 (Data Dictionary 전수 실측 정의)

### 3.1. 사용자 및 강의/수강 관리

#### (1) `users` (사용자 계정)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 사용자 고유번호 | BIGINT | N | PK | 자동 증가 (Identity) |
| `login_id` | 로그인 아이디 | VARCHAR(30) | N | UK | 영문/숫자 로그인 ID |
| `password_hash` | 비밀번호 해시 | VARCHAR(255) | N | | BCrypt 일방향 암호화 해시 |
| `name` | 사용자 이름 | VARCHAR(50) | N | | 학생 또는 교수 실명 |
| `role` | 역할 권한 | VARCHAR(20) | N | | `PROFESSOR`, `STUDENT` |
| `student_no` | 학번 | VARCHAR(30) | Y | | 학생의 경우 학번 |
| `department` | 소속 학과 | VARCHAR(100) | Y | | 소속 학과명 |
| `created_at` | 가입 일시 | DATETIME | N | | 계정 생성 타임스탬프 |

#### (2) `courses` (강의)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 강의 고유번호 | BIGINT | N | PK | 자동 증가 |
| `professor_id` | 담당 교수 번호 | BIGINT | N | FK | `users.id` 참조 |
| `name` | 강의명 | VARCHAR(100) | N | | 예: "인공지능 개론" |
| `semester` | 개설 학기 | VARCHAR(30) | N | | 예: "2026-2" |
| `invite_code` | 수강 초대 코드 | VARCHAR(12) | N | UK | 고유 난수 코드 |
| `ai_judge_enabled` | AI 판정 사용 여부 | BOOLEAN | N | | 기본값: false |
| `created_at` | 개설 일시 | DATETIME | N | | 강의 생성 타임스탬프 |

#### (3) `enrollments` (수강생 등록)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 등록 번호 | BIGINT | N | PK | 자동 증가 |
| `course_id` | 수강 강의 번호 | BIGINT | N | FK | `courses.id` 참조 (복합 UK) |
| `student_id` | 수강 학생 번호 | BIGINT | N | FK | `users.id` 참조 (복합 UK) |
| `joined_at` | 수강 신청 일시 | DATETIME | N | | 신청 완료 타임스탬프 |

---

### 3.2. 과제 및 버전 제출 관리

#### (4) `assignments` (과제)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 과제 고유번호 | BIGINT | N | PK | 자동 증가 |
| `course_id` | 소속 강의 번호 | BIGINT | N | FK | `courses.id` 참조 |
| `title` | 과제 제목 | VARCHAR(200) | N | | 과제명 |
| `description` | 과제 설명 | VARCHAR(4000) | Y | | 과제 요구사항 및 안내 |
| `due_at` | 정규 마감 일시 | DATETIME | N | | 정상 제출 기한 |
| `allow_late` | 지각 허용 여부 | BOOLEAN | N | | 지각 제출 승인 여부 |
| `late_until` | 지각 마감 일시 | DATETIME | Y | | 지각 최종 허용 시각 |
| `baseline_code` | 뼈대 코드 | LONGTEXT | Y | | 표절 검사 제외용 교수 제공 코드 |
| `team_project` | 조별과제 여부 | BOOLEAN | N | | 기본값: false |
| `max_team_size` | 최대 팀 인원수 | INT | Y | | null인 경우 무제한 |
| `created_at` | 등록 일시 | DATETIME | N | | 과제 등록 타임스탬프 |

#### (5) `submissions` (제출물 - 불변 모델)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 제출 고유번호 | BIGINT | N | PK | 자동 증가 |
| `assignment_id` | 과제 번호 | BIGINT | N | FK | `assignments.id` 참조 (복합 UK) |
| `student_id` | 제출 학생 번호 | BIGINT | N | FK | `users.id` 참조 (복합 UK) |
| `version_no` | 버전 번호 | INT | N | | `v1, v2, ...` 순차 채번 (복합 UK) |
| `message` | 제출 메모 | VARCHAR(500) | Y | | 학생 작성 커밋 메모 |
| `late` | 지각 제출 여부 | BOOLEAN | N | | 마감 후 제출 시 true |
| `submitted_at` | 제출 일시 | DATETIME | N | | 제출 완료 타임스탬프 |
| `source_repo` | GitHub 저장소 | VARCHAR(200) | Y | | GitHub 연동 시 "owner/repo" |
| `source_commit` | GitHub 커밋 SHA | VARCHAR(40) | Y | | 40자리 커밋 해시 |
| `source_ref` | 브랜치 또는 Ref | VARCHAR(200) | Y | | 제출 시 입력한 브랜치명/Ref |

#### (6) `submission_files` (제출 파일 목록)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 파일 고유번호 | BIGINT | N | PK | 자동 증가 |
| `submission_id` | 제출물 번호 | BIGINT | N | FK | `submissions.id` 참조 |
| `path` | 파일 내부 경로 | VARCHAR(300) | N | | 정규화된 파일 상대 경로 |
| `size` | 파일 크기 (Byte) | BIGINT | N | | 원본 파일 바이트 수 |
| `sha256` | SHA-256 해시 | VARCHAR(64) | N | | 파일 무결성 해시 |
| `charset` | 문자 인코딩 | VARCHAR(20) | N | | `UTF-8`, `CP949` |
| `content_text` | 텍스트 원문 | LONGTEXT | Y | | 텍스트 파일 소스코드 |
| `storage_path` | 스토리지 파일명 | VARCHAR(100) | N | | 디스크 저장 UUID 파일명 |

---

### 3.3. 코드 리뷰 및 피드백

#### (7) `review_comments` (줄 단위 리뷰 코멘트)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 코멘트 고유번호 | BIGINT | N | PK | 자동 증가 |
| `submission_file_id` | 대상 파일 번호 | BIGINT | N | FK | `submission_files.id` 참조 |
| `author_id` | 작성자 번호 | BIGINT | N | FK | `users.id` 참조 |
| `start_line` | 시작 줄 번호 | INT | N | | 피드백 대상 시작 라인 (1부터) |
| `end_line` | 끝 줄 번호 | INT | N | | 피드백 대상 끝 라인 (포함) |
| `body` | 코멘트 본문 | VARCHAR(2000) | N | | 피드백 본문 |
| `parent_id` | 부모 코멘트 번호 | BIGINT | Y | FK | Self-referencing (답글 스레드) |
| `resolved_by_student` | 학생 해결 표시 | BOOLEAN | N | | 학생 자율 확인 플래그 |
| `created_at` | 작성 일시 | DATETIME | N | | 코멘트 등록 타임스탬프 |

#### (8) `grades` (점수 및 총평)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 평가 고유번호 | BIGINT | N | PK | 자동 증가 |
| `submission_id` | 대상 제출물 번호 | BIGINT | N | FK | `submissions.id` 참조 |
| `professor_id` | 채점 교수 번호 | BIGINT | N | FK | `users.id` 참조 |
| `score` | 점수 (0~100) | INT | N | | 정수형 평가 점수 |
| `summary` | 총평 피드백 | VARCHAR(2000) | Y | | 버전 총평 의견 |
| `created_at` | 채점 일시 | DATETIME | N | | 채점 타임스탬프 |

---

### 3.4. 조별과제 (Team) 관리 (실제 엔티티 매핑)

#### (9) `teams` (과제 팀)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 팀 고유번호 | BIGINT | N | PK | 자동 증가 |
| `assignment_id` | 소속 과제 번호 | BIGINT | N | FK | `assignments.id` 참조 |
| `name` | 팀명 | VARCHAR(100) | N | | 팀 이름 (예: "A조 - RAG 챗봇") |
| `repo_url` | GitHub 저장소 URL | VARCHAR(300) | Y | | 팀 공식 GitHub 저장소 주소 |
| `leader_id` | 팀장 학생 번호 | BIGINT | N | FK | `users.id` 참조 |
| `created_at` | 팀 생성 일시 | DATETIME | N | | 생성 타임스탬프 |

#### (10) `team_members` (팀원 구성 및 담당 브랜치)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 멤버 고유번호 | BIGINT | N | PK | 자동 증가 |
| `assignment_id` | 과제 번호 | BIGINT | N | FK | `assignments.id` 참조 (복합 UK 1) |
| `team_id` | 소속 팀 번호 | BIGINT | N | FK | `teams.id` 참조 (복합 UK 2) |
| `student_id` | 팀원 학생 번호 | BIGINT | N | FK | `users.id` 참조 (복합 UK 1, 2) |
| `branch_name` | 담당 Git 브랜치 | VARCHAR(100) | Y | | 학생 담당 브랜치 (예: `feature/rag`) |
| `role_title` | 담당 역할명 | VARCHAR(100) | Y | | 담당 업무 (예: "임베딩 파이프라인") |
| `joined_at` | 팀 합류 일시 | DATETIME | N | | 합류 타임스탬프 |

---

### 3.5. Docker 자동 채점 관리

#### (11) `grading_configs` (채점 환경 설정)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 설정 고유번호 | BIGINT | N | PK | 자동 증가 |
| `assignment_id` | 대상 과제 번호 | BIGINT | N | FK | `assignments.id` 참조 (UK) |
| `language` | 실행 언어 | VARCHAR(20) | N | | `JAVA`, `PYTHON` |
| `main_file` | 실행 메인 파일명 | VARCHAR(200) | N | | 예: "Main.java", "main.py" |
| `time_limit_sec` | 제한 시간 (초) | INT | N | | 기본 3초 (무한 루프 방어) |
| `memory_limit_mb` | 메모리 제한 (MB) | INT | N | | 기본 256MB |

#### (12) `grading_test_cases` (테스트 케이스)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 케이스 고유번호 | BIGINT | N | PK | 자동 증가 |
| `assignment_id` | 소속 과제 번호 | BIGINT | N | FK | `assignments.id` 참조 |
| `name` | 케이스 이름 | VARCHAR(100) | N | | 케이스 명칭 |
| `input_text` | 입력 데이터 | LONGTEXT | Y | | stdin으로 주입될 데이터 |
| `expected_output` | 기대 출력 데이터 | LONGTEXT | N | | 정답 문자열 |
| `public_case` | 공개 케이스 여부 | BOOLEAN | N | | 학생 입출력 공개 여부 |
| `points` | 배점 | INT | N | | 해당 케이스 점수 |
| `order_no` | 정렬 순서 | INT | N | | 실행 순번 |

#### (13) `grading_runs` (채점 실행 이력)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 실행 고유번호 | BIGINT | N | PK | 자동 증가 |
| `submission_id` | 대상 제출물 번호 | BIGINT | N | FK | `submissions.id` 참조 |
| `status` | 채점 상태 | VARCHAR(20) | N | | `QUEUED`, `RUNNING`, `DONE`, `UNAVAILABLE`, `ERROR` |
| `compile_ok` | 컴파일 성공 여부 | BOOLEAN | Y | | Java 컴파일 결과 |
| `compile_message` | 컴파일 메시지 | VARCHAR(4000) | Y | | 컴파일 에러 출력 |
| `score` | 획득 점수 | INT | N | | 통과 케이스 배점 합 |
| `total_points` | 총 만점 | INT | N | | 전체 케이스 배점 합 |
| `passed_count` | 통과 케이스 수 | INT | N | | 정답 판정 개수 |
| `total_count` | 전체 케이스 수 | INT | N | | 전체 케이스 개수 |
| `runner` | 실행 러너 명칭 | VARCHAR(50) | Y | | "docker" 등 |
| `message` | 오류/상태 메시지 | VARCHAR(500) | Y | | 상태 설명 |
| `created_at` | 생성 일시 | DATETIME | N | | 큐 등록 타임스탬프 |
| `finished_at` | 완료 일시 | DATETIME | Y | | 채점 종료 타임스탬프 |

#### (14) `grading_results` (케이스별 실행 결과)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 결과 고유번호 | BIGINT | N | PK | 자동 증가 |
| `run_id` | 소속 채점 번호 | BIGINT | N | FK | `grading_runs.id` 참조 |
| `case_name` | 케이스 명칭 | VARCHAR(100) | N | | 당시 케이스 이름 |
| `public_case` | 공개 케이스 여부 | BOOLEAN | N | | 당시 공개 여부 |
| `points` | 배점 | INT | N | | 케이스 배점 |
| `awarded` | 획득 점수 | INT | N | | 획득한 점수 |
| `verdict` | 판정 결과 | VARCHAR(20) | N | | `PASSED`, `FAILED`, `TIMEOUT`, `RUNTIME_ERROR`, `COMPILE_ERROR` |
| `duration_ms` | 실행 소요 시간 | BIGINT | N | | 밀리초 단위 실행 시간 |
| `input_text` | 당시 입력값 | LONGTEXT | Y | | 실행 시 주입된 입력 |
| `expected_output` | 기대 출력값 | LONGTEXT | Y | | 정답 문자열 |
| `actual_output` | 실제 출력값 | LONGTEXT | Y | | 학생 프로그램 출력 (최대 64KB) |
| `error_output` | 표준 에러 출력 | LONGTEXT | Y | | stderr 에러 출력 |

---

### 3.6. 표절 검사 및 AI 판정

#### (15) `similarity_runs` (표절 검사 실행 이력)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 실행 고유번호 | BIGINT | N | PK | 자동 증가 |
| `assignment_id` | 대상 과제 번호 | BIGINT | N | FK | `assignments.id` 참조 |
| `threshold` | 유사도 임계값 | DOUBLE | N | | 기준치 (예: 0.70) |
| `compared` | 비교된 제출물 수 | INT | N | | 검사에 포함된 유효 제출물 개수 |
| `created_at` | 실행 일시 | DATETIME | N | | 검사 타임스탬프 |

#### (16) `similarity_pairs` (표절 의심 쌍)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 의심 쌍 고유번호 | BIGINT | N | PK | 자동 증가 |
| `run_id` | 소속 검사 번호 | BIGINT | N | FK | `similarity_runs.id` 참조 |
| `submission_a_id` | 제출물 A | BIGINT | N | FK | `submissions.id` 참조 |
| `submission_b_id` | 제출물 B | BIGINT | N | FK | `submissions.id` 참조 |
| `score` | 종합 유사도 | DOUBLE | N | | $\max(cov_A, cov_B)$ (0~1.0) |
| `coverage_a` | A 기준 포함도 | DOUBLE | N | | A 지문 중 B와 겹치는 비율 |
| `coverage_b` | B 기준 포함도 | DOUBLE | N | | B 지문 중 A와 겹치는 비율 |

#### (17) `ai_judgements` (AI 심층 판정 소견서)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `id` | 판정 고유번호 | BIGINT | N | PK | 자동 증가 |
| `comment_id` | 대상 코멘트 번호 | BIGINT | N | FK | `review_comments.id` 참조 (복합 UK) |
| `version_no` | 판정 시점 버전 | INT | N | | 판정 대상 최신 버전 (복합 UK) |
| `verdict` | 판정 결과 | VARCHAR(20) | N | | `RESOLVED`, `PARTIAL`, `NOT_RESOLVED` |
| `reason` | 판정 근거 사유 | VARCHAR(1000)| Y | | Gemini 생성 근거 1문장 |
| `model` | 사용 모델명 | VARCHAR(60) | Y | | `gemini-3.8-flash` 등 |
| `decision` | 교수 수락 여부 | VARCHAR(20) | N | | `PENDING`, `ACCEPTED`, `REJECTED` |
| `created_at` | 판정 일시 | DATETIME | N | | 생성 타임스탬프 |
| `decided_at` | 교수 결정 일시 | DATETIME | Y | | 수락/무시 결정 타임스탬프 |

#### (18) `ai_daily_usage` (서버 일일 AI 호출 집계)
| 컬럼명 | 논리명 | 데이터 타입 | Null | Key | 설명 |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `usage_date` | 사용 일자 | DATE | N | PK | 기준 일자 (예: `2026-11-07`) |
| `call_count` | 당일 호출 누적수 | INT | N | | 시스템 일일 호출 카운트 |
