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
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestData.class)
class ReviewGradeDiffTest {

    @Autowired TestData data;
    @Autowired SubmissionService submissionService;
    @Autowired CourseService courseService;
    @Autowired ReviewService reviewService;
    @Autowired GradeService gradeService;
    @Autowired DiffService diffService;

    AppUser prof;
    AppUser stu;
    AppUser classmate;
    AppUser otherProf;
    Course course;
    Assignment assignment;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        otherProf = data.professor();
        stu = data.student();
        classmate = data.student();
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        courseService.join(classmate, course.getInviteCode());
        assignment = data.assignment(course, prof, Duration.ofDays(2), false, null);
    }

    static Upload file(String name, String content) {
        return new Upload(name, content.getBytes(StandardCharsets.UTF_8));
    }

    SubmissionService.SubmitResult submit(Upload... files) {
        return submissionService.submit(assignment.getId(), stu, List.of(files), null);
    }

    Long fileIdOf(Long submissionId, String path) {
        return submissionService.view(submissionId, null, prof).tree().stream()
                .filter(n -> n.path().equals(path)).findFirst().orElseThrow().id();
    }

    // ---------- 코멘트 ----------

    @Test
    void 교수가_줄_범위에_코멘트를_남기고_학생이_답글을_단다() {
        var v1 = submit(file("A.java", "a\nb\nc\nd\n"));
        Long fid = fileIdOf(v1.id(), "A.java");

        Long cid = reviewService.addComment(fid, 2, 3, "  b, c 를 합치세요  ", prof);
        reviewService.reply(cid, "네 고치겠습니다", stu);
        reviewService.reply(cid, "확인했습니다", prof);

        var threads = reviewService.threads(fid, stu);
        assertThat(threads).hasSize(1);
        var t = threads.get(0);
        assertThat(t.startLine()).isEqualTo(2);
        assertThat(t.endLine()).isEqualTo(3);
        assertThat(t.root().body()).isEqualTo("b, c 를 합치세요");
        assertThat(t.root().professor()).isTrue();
        assertThat(t.replies()).extracting(ReviewService.CommentView::body).containsExactly("네 고치겠습니다", "확인했습니다");
        assertThat(t.replies().get(0).professor()).isFalse();
        // 화면용: 끝 줄(3) 아래에 붙는다
        assertThat(ReviewService.byEndLine(threads)).containsOnlyKeys(3);
        assertThat(submissionService.view(v1.id(), fid, stu).threadsByEndLine()).containsOnlyKeys(3);
    }

    @Test
    void 코멘트_권한_학생은_최상위_코멘트를_못쓰고_남의_제출물_코멘트는_못본다() {
        var v1 = submit(file("A.java", "a\nb\n"));
        Long fid = fileIdOf(v1.id(), "A.java");
        Long cid = reviewService.addComment(fid, 1, 1, "첫 줄", prof);

        assertThatThrownBy(() -> reviewService.addComment(fid, 1, 1, "학생이 씀", stu)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.addComment(fid, 1, 1, "다른 교수", otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.threads(fid, classmate)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.threads(fid, otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.reply(cid, "침입", classmate)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.reply(cid, "침입", otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.toggleResolved(cid, classmate)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reviewService.toggleResolved(cid, prof)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 코멘트_입력값_검증() {
        var v1 = submit(file("A.java", "a\nb\nc\n"), new Upload("img.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 1}));
        Long fid = fileIdOf(v1.id(), "A.java");
        Long png = fileIdOf(v1.id(), "img.png");

        assertThatThrownBy(() -> reviewService.addComment(fid, 0, 1, "x", prof)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reviewService.addComment(fid, 3, 2, "x", prof)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reviewService.addComment(fid, 1, 4, "x", prof)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reviewService.addComment(fid, 1, 1, "   ", prof)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reviewService.addComment(fid, 1, 1, "가".repeat(2001), prof)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reviewService.addComment(png, 1, 1, "x", prof)).isInstanceOf(BusinessException.class);
        assertThat(reviewService.threads(fid, prof)).isEmpty();
    }

    @Test
    void 답글에는_답글을_달수_없고_학생은_수정했어요_표시를_켜고_끈다() {
        var v1 = submit(file("A.java", "a\nb\n"));
        Long fid = fileIdOf(v1.id(), "A.java");
        Long cid = reviewService.addComment(fid, 1, 1, "고쳐주세요", prof);
        Long rid = reviewService.reply(cid, "네", stu);

        assertThatThrownBy(() -> reviewService.reply(rid, "답글의 답글", prof)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reviewService.toggleResolved(rid, stu)).isInstanceOf(BusinessException.class);

        assertThat(reviewService.toggleResolved(cid, stu)).isTrue();
        assertThat(reviewService.threads(fid, prof).get(0).resolved()).isTrue();
        assertThat(reviewService.toggleResolved(cid, stu)).isFalse();
    }

    // ---------- 점수 ----------

    @Test
    void 점수는_0에서_100만_허용하고_다시_저장하면_덮어쓴다() {
        var v1 = submit(file("A.java", "a\n"));
        gradeService.save(v1.id(), 0, "빈약", prof);
        gradeService.save(v1.id(), 100, null, prof);
        gradeService.save(v1.id(), 85, "  좋음  ", prof);

        var g = submissionService.view(v1.id(), null, stu).grade();
        assertThat(g.score()).isEqualTo(85);
        assertThat(g.summary()).isEqualTo("좋음");
        assertThat(g.professorName()).isEqualTo(prof.getName());

        for (Integer bad : new Integer[]{-1, 101, null}) {
            assertThatThrownBy(() -> gradeService.save(v1.id(), bad, "x", prof)).as(String.valueOf(bad))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(gradeService.score(v1.id())).isEqualTo(85);
    }

    @Test
    void 점수_입력_권한() {
        var v1 = submit(file("A.java", "a\n"));
        assertThatThrownBy(() -> gradeService.save(v1.id(), 50, "x", stu)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> gradeService.save(v1.id(), 50, "x", otherProf)).isInstanceOf(ForbiddenException.class);
        assertThat(gradeService.score(v1.id())).isNull();
    }

    @Test
    void 이력과_제출현황에_점수가_표시된다() {
        var v1 = submit(file("A.java", "a\n"));
        var v2 = submit(file("A.java", "b\n"));
        gradeService.save(v2.id(), 90, "굿", prof);

        var rows = submissionService.history(assignment.getId(), stu.getId(), stu).versions();
        assertThat(rows.get(0).score()).isEqualTo(90);   // v2
        assertThat(rows.get(1).score()).isNull();        // v1
        var status = submissionService.statusFor(assignment.getId(), prof).stream()
                .filter(s -> s.studentId().equals(stu.getId())).findFirst().orElseThrow();
        assertThat(status.score()).isEqualTo(90);
    }

    // ---------- diff ----------

    @Test
    void 두_버전을_비교하면_파일별_상태와_줄_변경이_나온다() {
        var v1 = submit(file("A.java", "a\nb\nc\n"), file("B.txt", "same\n"), file("Old.txt", "old\n"));
        var v2 = submit(file("A.java", "a\nB\nc\nd\n"), file("B.txt", "same\n"), file("New.txt", "new\n"));

        var d = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), null, "split", false, true, prof);

        assertThat(d.fromVersion()).isEqualTo(1);
        assertThat(d.toVersion()).isEqualTo(2);
        assertThat(d.changes()).extracting(DiffService.FileChange::path, DiffService.FileChange::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("A.java", "MODIFIED"),
                        org.assertj.core.groups.Tuple.tuple("B.txt", "UNCHANGED"),
                        org.assertj.core.groups.Tuple.tuple("New.txt", "ADDED"),
                        org.assertj.core.groups.Tuple.tuple("Old.txt", "REMOVED"));
        var a = d.changes().get(0);
        assertThat(a.added()).isEqualTo(2);     // B, d
        assertThat(a.removed()).isEqualTo(1);   // b
        assertThat(d.selectedPath()).isEqualTo("A.java");    // 변경된 첫 파일이 기본 선택
        assertThat(d.splitRows()).isNotEmpty();
        assertThat(d.splitRows().stream().anyMatch(r -> r.leftText() != null && r.leftText().equals("b")
                && r.rightText() != null && r.rightText().equals("B"))).isTrue();   // 수정 행으로 짝지음
    }

    @Test
    void 기본값은_최신_버전과_그_직전_버전이다() {
        var v1 = submit(file("A.java", "1\n"));
        var v2 = submit(file("A.java", "2\n"));
        var v3 = submit(file("A.java", "3\n"));
        var d = diffService.compare(assignment.getId(), stu.getId(), null, null, null, null, false, true, stu);
        assertThat(d.fromId()).isEqualTo(v2.id());
        assertThat(d.toId()).isEqualTo(v3.id());
        assertThat(d.mode()).isEqualTo("split");
        var any = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v3.id(), "A.java", "unified", false, true, stu);
        assertThat(any.fromVersion()).isEqualTo(1);
        assertThat(any.mode()).isEqualTo("unified");
        assertThat(any.unifiedRows()).extracting(DiffService.UnifiedRow::kind).containsExactly("-", "+");
    }

    @Test
    void 공백_무시를_켜면_들여쓰기만_바뀐_줄은_변경으로_보지_않는다() {
        var v1 = submit(file("A.java", "if (x) {\nfoo();\n}\n"));
        var v2 = submit(file("A.java", "if (x) {\n    foo();\n}\n"));
        var strict = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), "A.java", "split", false, true, prof);
        var lenient = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), "A.java", "split", true, true, prof);
        assertThat(strict.changes().get(0).status()).isEqualTo("MODIFIED");
        assertThat(lenient.changes().get(0).status()).isEqualTo("UNCHANGED");
    }

    @Test
    void 변경_없는_긴_구간은_접히고_펼치면_전부_보인다() {
        String base = String.join("\n", IntStream.rangeClosed(1, 100).mapToObj(i -> "line" + i).toList()) + "\n";
        String changed = base.replace("line50\n", "LINE50\n");
        var v1 = submit(file("A.java", base));
        var v2 = submit(file("A.java", changed));

        var folded = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), "A.java", "split", false, true, prof);
        var open = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), "A.java", "split", false, false, prof);
        var unified = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), "A.java", "unified", false, true, prof);

        assertThat(folded.splitRows().size()).isLessThan(20);
        assertThat(folded.splitRows().stream().filter(r -> r.type().name().equals("SKIPPED")).mapToInt(r -> r.skipped()).sum())
                .isEqualTo(100 - 7);    // 변경 1줄 + 앞뒤 3줄씩 = 7줄만 남음
        assertThat(open.splitRows()).hasSize(100);
        assertThat(unified.unifiedRows().stream().filter(r -> r.kind().equals("~"))).hasSize(2);
    }

    @Test
    void 비교_권한과_다른_학생_버전_거부() {
        var v1 = submit(file("A.java", "1\n"));
        var v2 = submit(file("A.java", "2\n"));
        var other = submissionService.submit(assignment.getId(), classmate, List.of(file("A.java", "x\n")), null);

        assertThatThrownBy(() -> diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), null, null, false, true, classmate))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), null, null, false, true, otherProf))
                .isInstanceOf(ForbiddenException.class);
        // 다른 학생의 제출을 내 이력 URL 에 끼워 넣어도 비교되지 않는다
        assertThatThrownBy(() -> diffService.compare(assignment.getId(), stu.getId(), v1.id(), other.id(), null, null, false, true, prof))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 바이너리_파일은_내용을_비교하지_않는다() {
        var v1 = submit(new Upload("img.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 1, 1}));
        var v2 = submit(new Upload("img.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 2, 2}));
        var d = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), null, null, false, true, prof);
        assertThat(d.selectedBinary()).isTrue();
        assertThat(d.splitRows()).isEmpty();
        assertThat(d.changes().get(0).status()).isEqualTo("MODIFIED");
    }

    @Test
    void 새_버전_파일의_코멘트가_비교_화면에_따라온다() {
        var v1 = submit(file("A.java", "a\nb\n"));
        var v2 = submit(file("A.java", "a\nB\n"));
        Long fid = fileIdOf(v2.id(), "A.java");
        reviewService.addComment(fid, 2, 2, "B 의 이름이 별로", prof);

        var d = diffService.compare(assignment.getId(), stu.getId(), v1.id(), v2.id(), "A.java", "split", false, true, stu);
        assertThat(d.toFileId()).isEqualTo(fid);
        assertThat(d.threadsByNewLine()).containsOnlyKeys(2);
    }
}
