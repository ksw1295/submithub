package com.submithub.demo;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Course;
import com.submithub.domain.CourseRepository;
import com.submithub.domain.Enrollment;
import com.submithub.domain.EnrollmentRepository;
import com.submithub.domain.Grade;
import com.submithub.domain.GradeRepository;
import com.submithub.domain.ReviewComment;
import com.submithub.domain.ReviewCommentRepository;
import com.submithub.domain.Role;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
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
    private final TransactionTemplate tx;

    public DemoDataLoader(UserRepository users, CourseRepository courses, EnrollmentRepository enrollments,
                          AssignmentRepository assignments, SubmissionRepository submissions,
                          ReviewCommentRepository comments, GradeRepository grades, FileStorage storage,
                          PasswordEncoder encoder, SimilarityService similarity, PlatformTransactionManager txm) {
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
        this.tx = new TransactionTemplate(txm);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        tx.executeWithoutResult(status -> seed());
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
        Course course = courses.save(new Course(prof, "자료구조", "2026-2", INVITE_CODE));
        for (User s : students) {
            enrollments.save(new Enrollment(course, s));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due1 = now.minusDays(3).withHour(23).withMinute(59).withSecond(0).withNano(0);
        Assignment a1 = assignments.save(new Assignment(course, "과제 1: 연결 리스트 구현",
                "단일 연결 리스트를 구현하세요. add, get, remove, size 가 동작해야 합니다.\n"
                        + "제공한 뼈대 코드(LinkedList.java)를 시작점으로 사용하세요.",
                due1, true, now.plusDays(30), DemoCode.BASELINE));
        LocalDateTime due2 = now.plusDays(3).withHour(23).withMinute(59).withSecond(0).withNano(0);
        Assignment a2 = assignments.save(new Assignment(course, "과제 2: 이진 탐색 트리",
                "정수를 저장하는 이진 탐색 트리를 구현하세요. insert 와 contains 가 필요합니다.",
                due2, false, null, null));

        User s1 = students.get(0), s2 = students.get(1), s3 = students.get(2), s4 = students.get(3),
                s5 = students.get(4), s7 = students.get(6);

        // ---- 과제 1 ----
        Submission s1v1 = submit(a1, s1, 1, "처음 제출", due1.minusHours(30).minusMinutes(10),
                files("LinkedList.java", DemoCode.S1_V1_LIST, "Main.java", DemoCode.S1_MAIN));
        Submission s1v2 = submit(a1, s1, 2, "리뷰 반영: get 범위, 디버그 출력 삭제, addFirst 추가",
                due1.minusHours(5).minusMinutes(25), files("LinkedList.java", DemoCode.S1_V2_LIST, "Main.java", DemoCode.S1_MAIN));
        Submission s1v3 = submit(a1, s1, 3, "변수명 정리", due1.minusHours(1).minusMinutes(20),
                files("LinkedList.java", DemoCode.S1_V3_LIST, "Main.java", DemoCode.S1_MAIN));

        Submission s2v1 = submit(a1, s2, 1, "연결 리스트 구현 완료", due1.minusHours(20).minusMinutes(40),
                files("LinkedList.java", DemoCode.ORIGINAL));
        Submission s3v1 = submit(a1, s3, 1, "제출합니다", due1.minusHours(2).minusMinutes(5),
                files("LinkedList.java", DemoCode.disguised(DemoCode.ORIGINAL)));
        Submission s4v1 = submit(a1, s4, 1, "늦어서 죄송합니다", due1.plusHours(7).plusMinutes(12),
                files("LinkedList.java", DemoCode.GENERIC_STYLE));
        Submission s5v1 = submit(a1, s5, 1, "이중 연결 리스트로 구현", due1.minusHours(27).minusMinutes(30),
                files("LinkedList.java", DemoCode.DOUBLY_V1));
        Submission s5v2 = submit(a1, s5, 2, "addFirst 수정", due1.minusMinutes(35),
                files("LinkedList.java", DemoCode.DOUBLY_V2));
        Submission s7v1 = submit(a1, s7, 1, "센티넬 노드 방식", due1.minusHours(1).minusMinutes(50),
                files("LinkedList.java", DemoCode.SENTINEL_STYLE));
        // student6(윤지우)은 과제 1 미제출

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
        Submission b1 = submit(a2, s1, 1, "재귀로 구현", now.minusHours(6), files("Bst.java", DemoCode.BST_RECURSIVE));
        submit(a2, s2, 1, "BST 완성", now.minusHours(2), files("Bst.java", DemoCode.BST_ITERATIVE));
        submit(a2, s5, 1, "insert, contains", now.minusMinutes(70), files("Bst.java", DemoCode.BST_RECURSIVE
                .replace("Node", "Entry").replace("key", "k")));
        comment(fileOf(b1, "Bst.java"), prof, line(S1(DemoCode.BST_RECURSIVE), "public boolean contains(int key) {"), null,
                "contains 도 재귀 버전으로 통일하면 읽기 쉽습니다.", null);

        // 표절 검사를 한 번 실행해 둔다 (시연 때 바로 결과를 볼 수 있도록)
        similarity.run(a1.getId(), SimilarityService.DEFAULT_THRESHOLD, new AppUser(prof));
    }

    // ---------- helpers ----------

    private static Map<String, String> files(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private Submission submit(Assignment a, User student, int version, String message, LocalDateTime at,
                              Map<String, String> files) {
        Submission s = new Submission(a, student, version, message, at.isAfter(a.getDueAt()), at);
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
