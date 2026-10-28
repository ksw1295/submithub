package com.submithub.demo;

import com.submithub.domain.AiJudgementRepository;
import com.submithub.domain.GradingRunRepository;
import com.submithub.domain.SimilarityPairRepository;
import com.submithub.domain.SimilarityRunRepository;
import com.submithub.domain.SubmissionFileRepository;
import com.submithub.domain.Team;
import com.submithub.domain.TeamMember;
import com.submithub.domain.TeamRepository;
import com.submithub.domain.TeamMemberRepository;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.CourseRepository;
import com.submithub.domain.Enrollment;
import com.submithub.domain.EnrollmentRepository;
import com.submithub.domain.Grade;
import com.submithub.domain.GradeRepository;
import com.submithub.domain.GradingConfig;
import com.submithub.domain.GradingConfigRepository;
import com.submithub.domain.GradingTestCase;
import com.submithub.domain.GradingTestCaseRepository;
import com.submithub.domain.ReviewComment;
import com.submithub.domain.ReviewCommentRepository;
import com.submithub.domain.Role;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
import com.submithub.service.GradingService;
import com.submithub.service.SimilarityService;
import com.submithub.storage.FileStorage;
import com.submithub.storage.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시연용 샘플 데이터. submithub.demo-data=true 이고 DB 에 사용자가 하나도 없을 때만 넣는다.
 *
 * 교수 1명(prof), 학생 7명(student1~7), 강의 1개(초대 코드 DEMO2026), 과제 2개.
 * 과제 1: 이민준은 3번에 걸쳐 제출하며 피드백이 일부만 반영됨, 최지호는 박서연의 코드를 베낌,
 *        정하은은 지각 제출, 윤지우는 미제출.
 * 과제 2: 진행 중, 일부 학생만 제출.
 */
@Component
@ConditionalOnProperty(prefix = "submithub", name = "demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    public static final String PROFESSOR_ID = "prof";
    public static final String PROFESSOR_PASSWORD = "prof1234!";
    public static final String STUDENT_PASSWORD = "student1234!";
    public static final String INVITE_CODE = "DEMO2026";

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final UserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final ReviewCommentRepository comments;
    private final GradeRepository grades;
    private final FileStorage storage;
    private final PasswordEncoder encoder;
    private final SimilarityService similarity;
    private final GradingConfigRepository gradingConfigs;
    private final GradingTestCaseRepository gradingCases;
    private final GradingService grading;
    private final TransactionTemplate tx;
    private final AiJudgementRepository judgements;
    private final SimilarityPairRepository similarityPairs;
    private final SimilarityRunRepository similarityRuns;
    private final GradingRunRepository gradingRuns;
    private final SubmissionFileRepository files;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final Object resetLock = new Object();
    /** 과제 1 의 제출 ID 들 (시드가 커밋된 뒤 자동 채점을 요청한다) */
    private final List<Long> gradedSubmissionIds = new ArrayList<>();

    public DemoDataLoader(UserRepository users, CourseRepository courses, EnrollmentRepository enrollments,
                          AssignmentRepository assignments, SubmissionRepository submissions,
                          ReviewCommentRepository comments, GradeRepository grades, FileStorage storage,
                          PasswordEncoder encoder, SimilarityService similarity,
                          GradingConfigRepository gradingConfigs, GradingTestCaseRepository gradingCases,
                          GradingService grading, PlatformTransactionManager txm,
                          AiJudgementRepository judgements, SimilarityPairRepository similarityPairs,
                          SimilarityRunRepository similarityRuns, GradingRunRepository gradingRuns,
                          SubmissionFileRepository files, TeamRepository teams, TeamMemberRepository teamMembers) {
        this.users = users;
        this.courses = courses;
        this.enrollments = enrollments;
        this.assignments = assignments;
        this.submissions = submissions;
        this.comments = comments;
        this.grades = grades;
        this.storage = storage;
        this.encoder = encoder;
        this.similarity = similarity;
        this.gradingConfigs = gradingConfigs;
        this.gradingCases = gradingCases;
        this.grading = grading;
        this.tx = new TransactionTemplate(txm);
        this.judgements = judgements;
        this.similarityPairs = similarityPairs;
        this.similarityRuns = similarityRuns;
        this.gradingRuns = gradingRuns;
        this.files = files;
        this.teams = teams;
        this.teamMembers = teamMembers;
    }

    /**
     * 시연 데이터 초기화. 실행 중 다른 요청과 충돌하지 않도록 락을 걸고,
     * DB와 업로드 파일을 삭제한 후 시연 데이터를 재구성한다.
     */
    public void resetDemoData() {
        synchronized (resetLock) {
            log.info("시연 데이터 초기화 시작...");
            tx.executeWithoutResult(status -> {
                teamMembers.deleteAllInBatch();
                teams.deleteAllInBatch();
                judgements.deleteAllInBatch();
                similarityPairs.deleteAllInBatch();
                similarityRuns.deleteAllInBatch();
                gradingRuns.deleteAllInBatch();
                grades.deleteAllInBatch();
                comments.deleteAllInBatch();
                files.deleteAllInBatch();
                submissions.deleteAllInBatch();
                gradingCases.deleteAllInBatch();
                gradingConfigs.deleteAllInBatch();
                assignments.deleteAllInBatch();
                enrollments.deleteAllInBatch();
                courses.deleteAllInBatch();
                users.deleteAllInBatch();
            });
            storage.clearAll();
            gradedSubmissionIds.clear();
            tx.executeWithoutResult(status -> seed());
            gradedSubmissionIds.forEach(grading::onNewVersion);
            log.info("시연 데이터 초기화 완료: 사용자 {}명, 강의 {}개 등록됨", users.count(), courses.count());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        tx.executeWithoutResult(status -> seed());
        // 시드가 커밋된 뒤 과제 1 의 제출물을 자동 채점한다: Docker 가 있으면 실제로 채점하고, 없으면 "채점 불가"로 기록한다
        gradedSubmissionIds.forEach(grading::onNewVersion);
        log.info("시연용 샘플 데이터를 넣었습니다. 교수 계정: {} / {}, 학생 계정: student1~student7 / {}, 초대 코드: {}",
                PROFESSOR_ID, PROFESSOR_PASSWORD, STUDENT_PASSWORD, INVITE_CODE);
    }

    void seed() {
        User prof = users.save(new User(PROFESSOR_ID, encoder.encode(PROFESSOR_PASSWORD), "김교수",
                Role.PROFESSOR, null, "컴퓨터공학과"));
        List<User> students = new ArrayList<>();
        for (int i = 0; i < DemoCode.NAMES.size(); i++) {
            students.add(users.save(new User("student" + (i + 1), encoder.encode(STUDENT_PASSWORD),
                    DemoCode.NAMES.get(i), Role.STUDENT, "2026" + String.format("%04d", 1001 + i), "컴퓨터공학과")));
        }
        List<String> extraNames = List.of(
                "송민서", "임도현", "오지민", "류현우", "서예은",
                "황시우", "배채원", "유진호", "권소율", "조태양"
        );
        List<User> extraStudents = new ArrayList<>();
        for (int i = 0; i < extraNames.size(); i++) {
            extraStudents.add(users.save(new User("student" + (DemoCode.NAMES.size() + i + 1),
                    encoder.encode(STUDENT_PASSWORD),
                    extraNames.get(i), Role.STUDENT, "2026" + String.format("%04d", 1001 + DemoCode.NAMES.size() + i), "컴퓨터공학과")));
        }
        Course course = courses.save(new Course(prof, "자료구조", "2026-2", INVITE_CODE));
        for (User s : students) {
            enrollments.save(new Enrollment(course, s));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due1 = now.minusDays(3).withHour(23).withMinute(59).withSecond(0).withNano(0);
        Assignment a1 = assignments.save(new Assignment(course, "과제 1: 연결 리스트 구현",
                DemoGrading.DESCRIPTION,
                due1, true, now.plusDays(30).toLocalDate().atTime(23, 59),
                DemoCode.BASELINE + "\n" + DemoCode.BASELINE_DRIVER));
        // 자동 채점 설정: 표준입력 명령을 처리하는 Main 의 출력을 비교한다
        gradingConfigs.save(new GradingConfig(a1, DemoGrading.LANGUAGE, DemoGrading.MAIN_FILE,
                DemoGrading.TIME_LIMIT_SECONDS, DemoGrading.MEMORY_LIMIT_MB));
        int order = 1;
        for (DemoGrading.Case c : DemoGrading.CASES) {
            gradingCases.save(new GradingTestCase(a1, c.name(), c.input(), c.expected(), c.publicCase(), c.points(), order++));
        }
        LocalDateTime due2 = now.plusDays(3).withHour(23).withMinute(59).withSecond(0).withNano(0);
        Assignment a2 = assignments.save(new Assignment(course, "과제 2: 이진 탐색 트리",
                "정수를 저장하는 이진 탐색 트리를 구현하세요. insert 와 contains 가 필요합니다.",
                due2, false, null, null));

        User s1 = students.get(0), s2 = students.get(1), s3 = students.get(2), s4 = students.get(3),
                s5 = students.get(4), s7 = students.get(6);

        // ---- 과제 1 ----
        Submission s1v1 = submit(a1, s1, 1, "처음 제출", at(due1, -2, 18, 0),
                files("LinkedList.java", DemoCode.S1_V1_LIST, "Main.java", DemoCode.S1_MAIN_V1));
        Submission s1v2 = submit(a1, s1, 2, "리뷰 반영: get 범위, 디버그 출력 삭제, addFirst 추가",
                at(due1, 0, 19, 0), files("LinkedList.java", DemoCode.S1_V2_LIST, "Main.java", DemoCode.S1_MAIN));
        Submission s1v3 = submit(a1, s1, 3, "변수명 정리", at(due1, 0, 22, 30),
                files("LinkedList.java", DemoCode.S1_V3_LIST, "Main.java", DemoCode.S1_MAIN));

        Submission s2v1 = submit(a1, s2, 1, "연결 리스트 구현 완료", at(due1, -1, 15, 0),
                files("LinkedList.java", DemoCode.ORIGINAL, "Main.java", DemoCode.MAIN_S2));
        Submission s3v1 = submit(a1, s3, 1, "제출합니다", at(due1, 0, 21, 0),
                files("LinkedList.java", DemoCode.disguised(DemoCode.ORIGINAL), "Main.java", DemoCode.MAIN_S3));
        Submission s4v1 = submit(a1, s4, 1, "늦어서 죄송합니다", at(due1, 1, 7, 0),
                files("LinkedList.java", DemoCode.GENERIC_STYLE, "Main.java", DemoCode.MAIN_S4));
        Submission s5v1 = submit(a1, s5, 1, "이중 연결 리스트로 구현", at(due1, -2, 21, 30),
                files("LinkedList.java", DemoCode.DOUBLY_V1, "Main.java", DemoCode.MAIN_S5));
        Submission s5v2 = submit(a1, s5, 2, "addFirst 수정", at(due1, 0, 23, 30),
                files("LinkedList.java", DemoCode.DOUBLY_V2, "Main.java", DemoCode.MAIN_S5));
        Submission s7v1 = submit(a1, s7, 1, "센티넬 노드 방식", at(due1, 0, 22, 0),
                files("LinkedList.java", DemoCode.SENTINEL_STYLE, "Main.java", DemoCode.MAIN_S7));
        // student6(윤지우)은 과제 1 미제출
        gradedSubmissionIds.addAll(List.of(s1v1.getId(), s1v2.getId(), s1v3.getId(), s2v1.getId(), s3v1.getId(),
                s4v1.getId(), s5v1.getId(), s5v2.getId(), s7v1.getId()));

        // 이민준 v1 에 대한 교수의 코멘트 4개 → v2, v3 제출 후 반영 상태가 자동 표시된다
        SubmissionFile f1 = fileOf(s1v1, "LinkedList.java");
        ReviewComment c1 = comment(f1, prof, line(S1(DemoCode.S1_V1_LIST), "for (int i = 0; i < index; i++) cur = cur.next;"), null,
                "index 가 범위를 벗어나면 NullPointerException 이 납니다. 범위 검사를 해주세요.", null);
        ReviewComment c2 = comment(f1, prof, line(S1(DemoCode.S1_V1_LIST), "int count = 0;"),
                line(S1(DemoCode.S1_V1_LIST), "while (cur != null) {"),
                "size() 가 호출할 때마다 전체를 순회합니다 (O(n)). size 를 필드로 관리해 보세요.", null);
        ReviewComment c3 = comment(f1, prof, line(S1(DemoCode.S1_V1_LIST), "System.out.println(\"debug"), null,
                "디버그 출력은 제출 전에 지워주세요.", null);
        comment(f1, prof, line(S1(DemoCode.S1_V1_LIST), "int x = 0;"), null,
                "변수명 x 는 의미를 알기 어렵습니다. 합계라는 뜻이 드러나게 바꿔주세요.", null);
        comment(f1, s1, c1.getStartLine(), null, "네, v2 에서 범위 검사를 추가했습니다.", c1);
        c3.setResolvedByStudent(true);
        comments.save(c3);
        // v3 에 대한 새 코멘트 (아직 고칠 기회가 없는 코멘트)
        comment(fileOf(s1v3, "LinkedList.java"), prof,
                line(S1(DemoCode.S1_V3_LIST), "public int size() {"), null,
                "나머지는 좋습니다. size() 만 필드로 관리하면 완벽해요.", null);

        comment(fileOf(s2v1, "LinkedList.java"), prof, line(S1(DemoCode.ORIGINAL), "StringBuilder sb"), null,
                "StringBuilder 를 쓴 점이 좋습니다.", null);
        comment(fileOf(s5v1, "LinkedList.java"), prof, line(S1(DemoCode.DOUBLY_V1), "e.next = front;"), null,
                "front.prev 도 갱신해야 역방향 순회가 깨지지 않습니다.", null);

        grade(s1v1, prof, 70, "기본 동작은 되지만 예외 처리가 부족합니다.");
        grade(s1v2, prof, 80, "지적한 부분을 잘 고쳤습니다.");
        grade(s1v3, prof, 88, "size() 만 개선하면 좋겠습니다.");
        grade(s2v1, prof, 95, "깔끔하고 예외 처리도 잘 되어 있습니다.");
        grade(s3v1, prof, 93, "잘 구현했습니다.");
        grade(s4v1, prof, 72, "제네릭을 시도한 점은 좋으나 기능이 부족하고 지각했습니다.");
        grade(s5v2, prof, 85, "수정 확인했습니다.");
        grade(s7v1, prof, 90, "센티넬 노드를 쓴 구현이 인상적입니다.");

        // ---- 과제 2 (진행 중) ----
        Submission b1 = submit(a2, s1, 1, "재귀로 구현", at(now, -1, 21, 0), files("Bst.java", DemoCode.BST_RECURSIVE));
        submit(a2, s2, 1, "BST 완성", at(now, -1, 23, 0), files("Bst.java", DemoCode.BST_ITERATIVE));
        submit(a2, s5, 1, "insert, contains", at(now, -1, 23, 30), files("Bst.java", DemoCode.BST_RECURSIVE
                .replace("Node", "Entry").replace("key", "k")));
        comment(fileOf(b1, "Bst.java"), prof, line(S1(DemoCode.BST_RECURSIVE), "public boolean contains(int key) {"), null,
                "contains 도 재귀 버전으로 통일하면 읽기 쉽습니다.", null);

        // 표절 검사를 한 번 실행해 둔다 (시연 때 바로 결과를 볼 수 있도록)
        similarity.run(a1.getId(), SimilarityService.DEFAULT_THRESHOLD, new AppUser(prof));

        // ---- 추가 교수진 및 강의 데이터 ----
        // 1) 김교수 추가 강의: 알고리즘 및 실습
        Course courseAlgo = courses.save(new Course(prof, "알고리즘 및 실습", "2026-2", "ALGO2026"));
        for (int i = 0; i < 5; i++) {
            enrollments.save(new Enrollment(courseAlgo, students.get(i)));
        }
        for (int i = 0; i < 5; i++) {
            enrollments.save(new Enrollment(courseAlgo, extraStudents.get(i)));
        }
        assignments.save(new Assignment(courseAlgo, "과제 1: 최단 경로 다익스트라(Dijkstra) 알고리즘",
                "주어진 가중치 방향 그래프에서 시작 정점부터 모든 정점까지의 최단 거리를 구하세요.",
                now.plusDays(7).withHour(23).withMinute(59).withSecond(0).withNano(0),
                true, now.plusDays(14).toLocalDate().atTime(23, 59), null));

        // 2) 김교수 추가 강의: 객체지향 설계 (지난 학기)
        Course courseOop = courses.save(new Course(prof, "객체지향 설계와 디자인패턴", "2026-1", "OOD2026"));
        for (User s : students) {
            enrollments.save(new Enrollment(courseOop, s));
        }
        assignments.save(new Assignment(courseOop, "과제 1: 전략 패턴을 활용한 결제 모듈 설계",
                "인터페이스 기반 Strategy Pattern을 적용하여 다양한 결제 수단을 유연하게 처리하는 구조를 작성하세요.",
                now.minusMonths(3).withHour(23).withMinute(59).withSecond(0).withNano(0),
                false, null, null));

        // 3) 추가 교수 1: 이교수 (인공지능학과)
        User prof2 = users.save(new User("prof2", encoder.encode(PROFESSOR_PASSWORD), "이교수",
                Role.PROFESSOR, null, "인공지능학과"));
        Course courseAi = courses.save(new Course(prof2, "인공지능 및 기계학습", "2026-2", "AI2026"));
        for (int i : List.of(0, 2, 3, 5)) {
            enrollments.save(new Enrollment(courseAi, students.get(i)));
        }
        Assignment aAi = assignments.save(new Assignment(courseAi, "과제 1: 대학 규정 질의응답 RAG 챗봇 시스템 구축",
                "대학 공개 규정(학칙, 장학 규정 등) 문서를 기반으로 학생 질의에 정확히 답변하는 RAG(검색 증강 생성) 챗봇을 구현하세요.\n- 벡터 검색(ChromaDB) 및 KURE-v1 임베딩 적용\n- LLM(Gemini) 연동 및 출처(근거 조항) 표기\n- Streamlit 기반 웹 인터페이스 구현",
                now.plusDays(10).withHour(23).withMinute(59).withSecond(0).withNano(0),
                true, now.plusDays(17).toLocalDate().atTime(23, 59), null,
                true, 4));

        // 이민준(student1) 1차 제출 (banjung-v1)
        Submission ragV1 = submit(aAi, s1, 1, "1차 제출: 대학 규정 RAG 챗봇 기본 파이프라인 구현",
                at(now, -2, 14, 0),
                "ksw1295/univ-rag-chatbot", "804a610d40c1a5f101065ae5104af92fe4443736",
                files("src/ask.py", DemoCode.RAG_V1_ASK,
                        "src/llm.py", DemoCode.RAG_V1_LLM,
                        "README.md", DemoCode.RAG_README));

        // 이교수 피드백 3건 등록
        SubmissionFile fAsk = fileOf(ragV1, "src/ask.py");
        SubmissionFile fLlm = fileOf(ragV1, "src/llm.py");

        comment(fAsk, prof2,
                line(S1(DemoCode.RAG_V1_ASK), "CHROMA_PATH = "),
                line(S1(DemoCode.RAG_V1_ASK), "DISTANCE_THRESHOLD = 0.35"),
                "로컬 절대 경로(D:\\dev\\...)가 하드코딩되어 있습니다. 환경 변수나 설정 모듈(common.py)을 만들어 분리해 주세요.", null);

        comment(fAsk, prof2,
                line(S1(DemoCode.RAG_V1_ASK), "client = chromadb.PersistentClient"),
                line(S1(DemoCode.RAG_V1_ASK), "model = SentenceTransformer"),
                "질문할 때마다 SentenceTransformer 모델을 다시 로드하면 심각한 지연이 발생합니다. 모델을 싱글톤으로 캐싱하여 재사용하도록 개선하세요.", null);

        comment(fLlm, prof2,
                line(S1(DemoCode.RAG_V1_LLM), "DEFAULT_MODEL = os.environ.get"), null,
                "gemini-2.5-flash 대신 무료 지원 기본 모델인 gemini-2.0-flash 로 변경하고 Streamlit Secrets 연동도 지원하도록 보완하세요.", null);

        // 이교수 1차 채점 (78점)
        grade(ragV1, prof2, 78,
                "RAG 검색 및 프롬프트 파이프라인은 잘 구현되었습니다. 다만 하드코딩된 경로 제거, 임베딩 모델 캐싱, 그리고 배포 환경(Streamlit Secrets) 연동을 개선해 주면 좋겠습니다.");

        // 이민준(student1) 2차 제출 (code-review-v1: 리뷰 100% 반영)
        Submission ragV2 = submit(aAi, s1, 2, "2차 제출 (리뷰 반영): common.py 경로 공통화, 모델 싱글톤 캐싱, gemini-2.0-flash 및 Streamlit Secrets 지원",
                at(now, -1, 17, 30),
                "ksw1295/univ-rag-chatbot", "fa3aa03e694db87a603315ccf3aca06c999106ee",
                files("src/ask.py", DemoCode.RAG_V2_ASK,
                        "src/llm.py", DemoCode.RAG_V2_LLM,
                        "src/common.py", DemoCode.RAG_V2_COMMON,
                        "README.md", DemoCode.RAG_README));

        // ---- 조별 과제 (팀 프로젝트) 및 브랜치 매핑 구성 ----
        Team team1 = teams.save(new Team(aAi, "1조 - 대학 RAG 챗봇팀", "ksw1295/univ-rag-chatbot", s1));
        teamMembers.save(new TeamMember(team1, s1, "feature/rag-pipeline", "AI 엔진 & RAG 파이프라인 (팀장)"));
        teamMembers.save(new TeamMember(team1, s3, "feature/streamlit-ui", "Streamlit 웹 대시보드 인터페이스"));
        teamMembers.save(new TeamMember(team1, s4, "feature/rest-api", "FastAPI 규정 검색 API 서버"));

        // 최지호(student3) 1차 제출
        Submission ragS3 = submit(aAi, s3, 1, "1차 제출: 대학 규정 질의응답 웹 인터페이스",
                at(now, -1, 20, 0),
                "ksw1295/univ-rag-chatbot", "7953452f1437bb96f86236b28dd4f19b16ea9825",
                files("src/ask.py", DemoCode.disguised(DemoCode.RAG_V1_ASK), "README.md", DemoCode.RAG_README));
        grade(ragS3, prof2, 85, "UI 레이아웃이 깔끔하고 질문/답변 흐름이 직관적입니다.");

        // 4) 추가 교수 2: 박교수 (소프트웨어학부)
        User prof3 = users.save(new User("prof3", encoder.encode(PROFESSOR_PASSWORD), "박교수",
                Role.PROFESSOR, null, "소프트웨어학부"));
        Course courseWeb = courses.save(new Course(prof3, "웹 백엔드 아키텍처", "2026-2", "WEB2026"));
        for (int i : List.of(1, 4, 6)) {
            enrollments.save(new Enrollment(courseWeb, students.get(i)));
        }
        assignments.save(new Assignment(courseWeb, "과제 1: REST API 엔드포인트 및 인증 필터 구현",
                "RESTful 원칙에 부합하는 리소스 URI 및 Spring Security 인증 필터 체인을 설계하세요.",
                now.plusDays(12).withHour(23).withMinute(59).withSecond(0).withNano(0),
                false, null, null));
    }

    // ---------- helpers ----------

    /** base 의 날짜에서 dayOffset 일 뒤(음수면 앞) 날짜의 hour:minute (분 단위가 어색하지 않게 정각/30분으로 쓴다) */
    private static LocalDateTime at(LocalDateTime base, int dayOffset, int hour, int minute) {
        return base.toLocalDate().plusDays(dayOffset).atTime(hour, minute);
    }

    private static Map<String, String> files(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private Submission submit(Assignment a, User student, int version, String message, LocalDateTime at,
                              Map<String, String> files) {
        return submit(a, student, version, message, at, null, null, files);
    }

    private Submission submit(Assignment a, User student, int version, String message, LocalDateTime at,
                              String sourceRepo, String sourceCommit, Map<String, String> files) {
        Submission s = new Submission(a, student, version, message, at.isAfter(a.getDueAt()), at);
        if (sourceRepo != null && sourceCommit != null) {
            s.setSource(sourceRepo, sourceCommit);
        }
        files.forEach((path, text) -> {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            s.addFile(new SubmissionFile(s, path, bytes.length, Hashing.sha256(bytes), "UTF-8", text,
                    storage.store(bytes)));
        });
        return submissions.saveAndFlush(s);
    }

    private static SubmissionFile fileOf(Submission s, String path) {
        return s.getFiles().stream().filter(f -> f.getPath().equals(path)).findFirst().orElseThrow();
    }

    private ReviewComment comment(SubmissionFile file, User author, int start, Integer end, String body,
                                  ReviewComment parent) {
        return comments.save(new ReviewComment(file, author, start, end == null ? start : end, body, parent));
    }

    private void grade(Submission s, User prof, int score, String summary) {
        grades.save(new Grade(s, prof, score, summary));
    }

    /** 코드 문자열에서 needle 이 처음 나오는 줄 번호 (1부터) */
    private static int line(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i + 1;
            }
        }
        throw new IllegalStateException("샘플 코드에서 찾을 수 없음: " + needle);
    }

    private static List<String> S1(String code) {
        return com.submithub.core.text.LineSplitter.split(code);
    }
}
