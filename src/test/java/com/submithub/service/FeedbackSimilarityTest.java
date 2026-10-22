package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.security.AppUser;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestData.class)
class FeedbackSimilarityTest {

    @Autowired TestData data;
    @Autowired SubmissionService submissionService;
    @Autowired CourseService courseService;
    @Autowired ReviewService reviewService;
    @Autowired FeedbackService feedbackService;
    @Autowired SimilarityService similarityService;
    @Autowired AssignmentService assignmentService;

    AppUser prof, otherProf, stu, stu2, stu3;
    Course course;
    Assignment assignment;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        otherProf = data.professor();
        stu = data.student();
        stu2 = data.student();
        stu3 = data.student();
        course = data.course(prof);
        for (AppUser s : List.of(stu, stu2, stu3)) {
            courseService.join(s, course.getInviteCode());
        }
        assignment = data.assignment(course, prof, Duration.ofDays(2), false, null);
    }

    static Upload file(String name, String content) {
        return new Upload(name, content.getBytes(StandardCharsets.UTF_8));
    }

    SubmissionService.SubmitResult submit(AppUser who, Upload... files) {
        return submissionService.submit(assignment.getId(), who, List.of(files), null);
    }

    Long fileId(Long submissionId, String path) {
        return submissionService.view(submissionId, null, prof).tree().stream()
                .filter(n -> n.path().equals(path)).findFirst().orElseThrow().id();
    }

    FeedbackService.TrackedComment find(FeedbackService.FeedbackView v, Long commentId) {
        return v.items().stream().filter(t -> t.commentId().equals(commentId)).findFirst().orElseThrow();
    }

    // ---------- 피드백 반영 추적 ----------

    @Test
    void 새_버전을_내면_이전_코멘트의_반영_상태가_자동_판정된다() {
        var v1 = submit(stu, file("A.java", "l1\nl2\nl3\nl4\nl5\nl6\nl7\n"));
        Long f1 = fileId(v1.id(), "A.java");
        Long keep = reviewService.addComment(f1, 2, 2, "l2 그대로인 줄", prof);        // 미반영
        Long edit = reviewService.addComment(f1, 4, 4, "l4 를 고쳐주세요", prof);        // 반영됨
        Long del = reviewService.addComment(f1, 6, 7, "l6~l7 은 필요 없어요", prof);     // 삭제됨

        // v2: 위에 2줄 추가(줄 번호 이동), l4 수정, l6~l7 삭제
        submit(stu, file("A.java", "new1\nnew2\nl1\nl2\nl3\nL4-fixed\nl5\n"));

        var fb = feedbackService.forStudent(assignment.getId(), stu.getId(), stu);
        var tKeep = find(fb, keep);
        assertThat(tKeep.status()).isEqualTo("UNCHANGED");
        assertThat(tKeep.latestStart()).isEqualTo(4);          // 2 → 4 (줄 번호가 이동했어도 따라간다)
        assertThat(tKeep.latestVersion()).isEqualTo(2);
        var tEdit = find(fb, edit);
        assertThat(tEdit.status()).isEqualTo("MODIFIED");
        assertThat(tEdit.changedAtVersion()).isEqualTo(2);
        assertThat(find(fb, del).status()).isEqualTo("DELETED");

        var s = fb.summary();
        assertThat(s.total()).isEqualTo(3);
        assertThat(s.unchanged()).isEqualTo(1);
        assertThat(s.modified()).isEqualTo(1);
        assertThat(s.deleted()).isEqualTo(1);
        assertThat(s.remaining()).isEqualTo(1);              // 학생의 "남은 피드백"
        assertThat(s.ratePercent()).isEqualTo(67);           // 3개 중 2개 반영
    }

    @Test
    void 들여쓰기만_바꾼_것은_반영으로_보지_않는다() {
        var v1 = submit(stu, file("A.java", "void f() {\nint x = 1;\n}\n"));
        Long c = reviewService.addComment(fileId(v1.id(), "A.java"), 2, 2, "변수명 개선", prof);
        submit(stu, file("A.java", "void f() {\n        int x = 1;\n}\n"));

        var t = find(feedbackService.forStudent(assignment.getId(), stu.getId(), stu), c);
        assertThat(t.status()).isEqualTo("UNCHANGED");
        assertThat(t.latestStart()).isEqualTo(2);
    }

    @Test
    void 코멘트가_최신_버전에_달렸으면_아직_고칠_기회가_없어_반영률에서_빠진다() {
        var v1 = submit(stu, file("A.java", "a\nb\n"));
        Long c = reviewService.addComment(fileId(v1.id(), "A.java"), 1, 1, "첫 코멘트", prof);

        var fb = feedbackService.forStudent(assignment.getId(), stu.getId(), stu);
        assertThat(find(fb, c).status()).isEqualTo("PENDING");
        assertThat(fb.summary().tracked()).isZero();
        assertThat(fb.summary().hasRate()).isFalse();
        assertThat(fb.summary().remaining()).isEqualTo(1);    // 새 코멘트도 아직 할 일
    }

    @Test
    void 여러_버전에_걸쳐_처음_바뀐_버전을_알려주고_파일이_사라지면_삭제로_본다() {
        var v1 = submit(stu, file("A.java", "a\nb\nc\n"), file("B.java", "x\ny\n"));
        Long a = reviewService.addComment(fileId(v1.id(), "A.java"), 2, 2, "b 수정", prof);
        Long b = reviewService.addComment(fileId(v1.id(), "B.java"), 1, 1, "x", prof);
        submit(stu, file("A.java", "a\nb\nc\n"), file("B.java", "x\ny\n"));                   // v2: 변화 없음
        var v3 = submit(stu, file("A.java", "a\nB!\nc\n"));                                   // v3: A 수정, B 파일 삭제

        var fb = feedbackService.forStudent(assignment.getId(), stu.getId(), stu);
        assertThat(find(fb, a).status()).isEqualTo("MODIFIED");
        assertThat(find(fb, a).changedAtVersion()).isEqualTo(3);
        assertThat(find(fb, b).status()).isEqualTo("DELETED");
        assertThat(find(fb, b).changedAtVersion()).isEqualTo(3);
        assertThat(fb.summary().ratePercent()).isEqualTo(100);
        assertThat(v3.versionNo()).isEqualTo(3);
    }

    @Test
    void 제출_보기에서_이전_버전_코멘트에_반영_상태가_붙는다() {
        var v1 = submit(stu, file("A.java", "a\nb\nc\n"));
        Long f1 = fileId(v1.id(), "A.java");
        Long c = reviewService.addComment(f1, 3, 3, "c 점검", prof);
        assertThat(submissionService.view(v1.id(), f1, prof).tracking()).isEmpty();      // 아직 새 버전 없음
        submit(stu, file("A.java", "a\nb\nc\nd\n"));

        var tr = submissionService.view(v1.id(), f1, prof).tracking();
        assertThat(tr).containsOnlyKeys(c);
        assertThat(tr.get(c).status()).isEqualTo("UNCHANGED");
    }

    @Test
    void 교수는_학생별_반영률을_볼수_있고_다른_학생은_남의_피드백을_볼수_없다() {
        var v1 = submit(stu, file("A.java", "a\nb\n"));
        submit(stu2, file("A.java", "a\nb\n"));
        reviewService.addComment(fileId(v1.id(), "A.java"), 1, 1, "고쳐요", prof);
        submit(stu, file("A.java", "A\nb\n"));

        var summaries = feedbackService.summaries(assignment.getId(), prof);
        assertThat(summaries).containsOnlyKeys(stu.getId());
        assertThat(summaries.get(stu.getId()).ratePercent()).isEqualTo(100);

        assertThatThrownBy(() -> feedbackService.forStudent(assignment.getId(), stu.getId(), stu2))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> feedbackService.forStudent(assignment.getId(), stu.getId(), otherProf))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> feedbackService.summaries(assignment.getId(), otherProf))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> feedbackService.summaries(assignment.getId(), stu))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- 표절 검사 ----------

    static final String SKELETON = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    Scanner sc = new Scanner(System.in);
                    // TODO: 여기부터 작성
                }
            }
            """;

    static final String SOLUTION = """
            import java.util.ArrayList;
            import java.util.List;

            public class Stats {
                static int sum(List<Integer> values) {
                    int total = 0;
                    for (int i = 0; i < values.size(); i++) {
                        if (values.get(i) > 0) {
                            total += values.get(i);
                        } else {
                            total -= 1;
                        }
                    }
                    return total;
                }

                static double average(List<Integer> values) {
                    if (values.isEmpty()) {
                        return 0;
                    }
                    return (double) sum(values) / values.size();
                }

                public static void main(String[] args) {
                    List<Integer> data = new ArrayList<>();
                    for (int i = 1; i <= 10; i++) {
                        data.add(i * 3);
                    }
                    System.out.println("합계: " + sum(data));
                    System.out.println("평균: " + average(data));
                }
            }
            """;

    /** 변수명, 주석, 들여쓰기, 문자열을 바꿔서 베낀 코드 */
    static final String DISGUISED = SOLUTION
            .replace("values", "nums").replace("total", "acc").replace("data", "items")
            .replace("Stats", "Calc").replace("sum", "add").replace("average", "mean")
            .replace("합계", "Sum is").replace("평균", "Avg is")
            .replace("    ", "\t")
            .replace("static int", "// 합을 구함\nstatic int");

    static final String DIFFERENT = """
            import java.util.HashMap;
            import java.util.Map;

            public class Words {
                public static void main(String[] args) {
                    Map<String, Integer> freq = new HashMap<>();
                    String text = "the quick brown fox jumps over the lazy dog the end";
                    for (String w : text.split(" ")) {
                        freq.merge(w, 1, Integer::sum);
                    }
                    String best = null;
                    int max = 0;
                    for (Map.Entry<String, Integer> e : freq.entrySet()) {
                        if (e.getValue() > max) {
                            max = e.getValue();
                            best = e.getKey();
                        }
                    }
                    System.out.println(best + " " + max);
                }
            }
            """;

    @Test
    void 변수명과_주석을_바꿔도_베낀_쌍을_찾고_다른_코드는_제외한다() {
        submit(stu, file("Main.java", SOLUTION));
        submit(stu2, file("Main.java", DISGUISED));
        submit(stu3, file("Main.java", DIFFERENT));

        var run = similarityService.run(assignment.getId(), 0.6, prof);

        assertThat(run.compared()).isEqualTo(3);
        assertThat(run.pairs()).hasSize(1);
        var p = run.pairs().get(0);
        assertThat(java.util.Set.of(p.studentA(), p.studentB())).containsExactlyInAnyOrder(stu.getName(), stu2.getName());
        assertThat(p.score()).isGreaterThan(0.8);

        // 저장되어 다시 불러올 수 있다
        var again = similarityService.latest(assignment.getId(), prof);
        assertThat(again.runId()).isEqualTo(run.runId());
        assertThat(again.pairs()).hasSize(1);
    }

    @Test
    void 쌍_비교_화면은_겹치는_줄을_표시한다() {
        submit(stu, file("Main.java", SOLUTION));
        submit(stu2, file("Main.java", DISGUISED));
        var run = similarityService.run(assignment.getId(), 0.6, prof);

        var view = similarityService.pair(run.pairs().get(0).id(), prof);

        assertThat(view.filesA()).hasSize(1);
        assertThat(view.filesA().get(0).path()).isEqualTo("Main.java");
        long matchedA = view.filesA().get(0).lines().stream().filter(SimilarityService.CodeLine::matched).count();
        long matchedB = view.filesB().get(0).lines().stream().filter(SimilarityService.CodeLine::matched).count();
        assertThat(matchedA).isGreaterThan(15).isEqualTo(view.matchedLinesA());
        assertThat(matchedB).isGreaterThan(15).isEqualTo(view.matchedLinesB());
        assertThat(view.filesA().get(0).lines().get(0).no()).isEqualTo(1);       // 파일 안의 줄 번호
        assertThat(view.filesA().get(0).lines().get(0).text()).startsWith("import");
    }

    @Test
    void 교수가_올려둔_뼈대_코드는_비교에서_제외된다() {
        // 뼈대만 거의 그대로 낸 두 학생: 뼈대를 모르면 매우 비슷, 알면 비슷하지 않음
        String skeletonLong = SKELETON + """

                class Util {
                    static int twice(int x) { return x * 2; }
                    static int thrice(int x) { return x * 3; }
                    static boolean positive(int x) { return x > 0; }
                    static boolean even(int x) { return x % 2 == 0; }
                }
                """;
        submit(stu, file("Main.java", skeletonLong + "\n// A 만의 코드\nclass A1 { int f() { return 1 + 2; } }\n"));
        submit(stu2, file("Main.java", skeletonLong + "\n// B 만의 코드\nclass B1 { String g() { return \"b\".repeat(3); } }\n"));

        var without = similarityService.run(assignment.getId(), 0.6, prof);
        assertThat(without.pairs()).hasSize(1);        // 뼈대 때문에 의심 쌍으로 나온다

        assignmentService.update(assignment.getId(), prof, new AssignmentService.AssignmentInput(
                assignment.getTitle(), "", LocalDateTime.now().plusDays(2), false, null, skeletonLong, null));
        var with = similarityService.run(assignment.getId(), 0.6, prof);
        assertThat(with.hasBaseline()).isTrue();
        assertThat(with.pairs()).isEmpty();            // 뼈대 제외 후에는 없음
    }

    @Test
    void 표절_검사는_학생별_최신_버전만_쓴다() {
        submit(stu, file("Main.java", SOLUTION));
        submit(stu2, file("Main.java", DISGUISED));
        assertThat(similarityService.run(assignment.getId(), 0.6, prof).pairs()).hasSize(1);

        submit(stu2, file("Main.java", DIFFERENT));      // 다시 제출: 최신 버전이 달라짐
        assertThat(similarityService.run(assignment.getId(), 0.6, prof).pairs()).isEmpty();
    }

    @Test
    void 표절_검사_권한과_입력_검증() {
        submit(stu, file("Main.java", SOLUTION));
        submit(stu2, file("Main.java", DISGUISED));
        var run = similarityService.run(assignment.getId(), 0.6, prof);
        Long pairId = run.pairs().get(0).id();

        assertThatThrownBy(() -> similarityService.run(assignment.getId(), 0.6, stu)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> similarityService.run(assignment.getId(), 0.6, otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> similarityService.latest(assignment.getId(), otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> similarityService.pair(pairId, stu)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> similarityService.pair(pairId, otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> similarityService.pair(999999L, prof)).isInstanceOf(NotFoundException.class);
        for (double bad : new double[]{0.0, 1.5, -1, Double.NaN}) {
            assertThatThrownBy(() -> similarityService.run(assignment.getId(), bad, prof)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void 제출이_하나뿐이면_비교_대상이_부족하다() {
        submit(stu, file("Main.java", SOLUTION));
        var run = similarityService.run(assignment.getId(), 0.6, prof);
        assertThat(run.compared()).isEqualTo(1);
        assertThat(run.pairs()).isEmpty();
    }
}
