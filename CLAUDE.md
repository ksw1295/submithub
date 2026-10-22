# SubmitHub

과제 제출을 Git처럼 관리하는 졸업작품 웹 서비스. 1인 개발(강서원).
전체 설계와 단계별 계획은 `docs/PLAN.md` — 작업 전에 반드시 읽고, 끝난 항목은 체크한다.

## 현재 상태

- `src/main/java/com/submithub/core/**` 와 그 테스트는 **완료**되어 있다 (순수 Java, Spring 의존 없음).
  - Myers diff, 좌우 분할 행 생성, 피드백 반영 추적(LineTracker), Winnowing 표절 검사, UTF-8/CP949 디코딩
  - 테스트 28개 통과 확인됨. 동작을 바꿀 때는 테스트도 같이 고친다.
- PLAN.md 7장 1~6주차 **구현 완료** (필수 항목 전부). 웹 서비스(회원·강의·과제·제출·diff·코드 리뷰·반영 추적·표절 검사·통계)와
  시연 데이터(`demo/`), Docker Compose 구성, README 가 있다. `mvn test` 전체 통과 (113개).
  "여유가 있으면" 항목(자동 채점, AI 피드백, 메일 알림)은 하지 않았다.
- Docker Compose 는 개발 PC 에 Docker 가 없어 실제 실행은 확인하지 못했다. MariaDB 프로필도 H2(MariaDB 모드)로만 검증했다.
- GitHub 원격 저장소는 아직 연결하지 않았다 (`git remote add origin ...` 필요).

## 개발 메모

- 이 PC 에서 Maven: `D:\Dev\Tools\apache-maven-3.9.9\bin\mvn.cmd`, JDK 17: `D:\Dev\Tools\java\jdk-17.0.12` (JAVA_HOME 설정 필요).
- 로컬 실행은 `mvn spring-boot:run` (H2 파일 DB `./data`, 시연 데이터 자동 입력). 테스트 계정은 README 참고.
- Thymeleaf: 같은 요소에 `th:replace` 와 `th:if`/`th:unless` 를 같이 쓰면 `th:replace` 가 먼저 처리되어 조건이 무시된다.
  조건 블록(`<th:block th:if>`)으로 감싸고 안에서 `th:replace` 를 쓴다. SpEL 에서 `char` 와 문자열 `'+'` 은 같다고 비교되지 않는다.
- `.gitignore` 의 업로드/DB 폴더 패턴은 루트 기준(`/uploads/`, `/data/`)으로 쓴다. 기준 없는 `submissions/` 는 화면 폴더까지 무시했었다.
- `open-in-view` 가 꺼져 있으므로 화면에는 엔티티 대신 서비스가 만든 DTO(record)를 넘긴다.

## 규칙

- Java 17, Spring Boot 3.5.x, Maven, Thymeleaf, Spring Data JPA, MariaDB(운영) / H2(테스트)
- `core` 패키지는 Spring 에 의존하지 않게 유지한다. 서비스 계층에서 가져다 쓴다.
- 제출(submissions)은 INSERT 만 한다. 기존 버전을 수정·삭제하지 않는다.
- 모든 제출물·코멘트 조회는 권한 검사를 거친다: 본인 것이거나, 해당 강의의 교수만.
- 업로드 파일은 UUID 이름으로 저장하고, zip 내부 경로는 정규화해서 저장 폴더 밖으로 못 나가게 한다.
- 비밀번호는 BCrypt. DB 접속 정보는 `application-local.yml` 이나 환경변수로 두고 커밋하지 않는다.
- 단계마다 `mvn test` 통과 후 커밋한다. 커밋 메시지는 한국어로 무엇을 했는지 짧게.
- 사용자(강서원)에게 설명은 한국어로 한다.

## 참고: 기존 Swing 버전

`D:\Dev\Workspaces\eclipse_workspace\src\com\capstone` (소스),
`D:\Dev\Workspaces\capstone\sql\schema.sql` (스키마). 새 프로젝트에 복사하지 말고 설계 참고용으로만 본다.
