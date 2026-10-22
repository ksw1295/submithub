package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.security.AppUser;
import com.submithub.storage.UploadProcessor.Upload;
import com.submithub.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestData.class)
class SubmissionServiceTest {

    @Autowired TestData data;
    @Autowired SubmissionService submissionService;
    @Autowired CourseService courseService;
    @Autowired SubmissionRepository submissions;

    AppUser prof;
    AppUser stu;
    Course course;
    Assignment open;

    @BeforeEach
    void setUp() {
        prof = data.professor();
        stu = data.student();
        course = data.course(prof);
        courseService.join(stu, course.getInviteCode());
        open = data.assignment(course, prof, Duration.ofDays(2), false, null);
    }

    static Upload text(String name, String content) {
        return new Upload(name, content.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] zip(Charset cs, String[]... entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos, cs)) {
            for (String[] e : entries) {
                z.putNextEntry(new ZipEntry(e[0]));
                z.write(e[1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    void 제출할때마다_버전이_1씩_올라가고_이전_버전은_그대로다() {
        var v1 = submissionService.submit(open.getId(), stu, List.of(text("Main.java", "class A {}\n")), "첫 제출");
        var v2 = submissionService.submit(open.getId(), stu, List.of(text("Main.java", "class B {}\n")), "수정");

        assertThat(v1.versionNo()).isEqualTo(1);
        assertThat(v2.versionNo()).isEqualTo(2);

        var first = submissionService.view(v1.id(), null, stu);
        assertThat(first.selectedLines()).containsExactly("class A {}");
        assertThat(first.message()).isEqualTo("첫 제출");
        assertThat(submissionService.view(v2.id(), null, stu).selectedLines()).containsExactly("class B {}");
        assertThat(submissions.countByAssignmentIdAndStudentId(open.getId(), stu.getId())).isEqualTo(2);
    }

    @Test
    void 동시에_여러번_제출해도_버전_번호가_겹치지_않는다() throws Exception {
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Callable<Integer>> tasks = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            tasks.add(() -> submissionService.submit(open.getId(), stu,
                    List.of(text("Main.java", "int x = " + seq.incrementAndGet() + ";\n")), "동시").versionNo());
        }
        List<Integer> versions = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(tasks)) {
            versions.add(f.get());   // 예외가 났다면 여기서 터진다
        }
        pool.shutdown();

        assertThat(versions).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void 마감_후에는_제출할수_없다() {
        Assignment closed = data.assignment(course, prof, Duration.ofHours(-1), false, null);
        assertThatThrownBy(() -> submissionService.submit(closed.getId(), stu, List.of(text("a.txt", "x")), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("마감");
        assertThat(submissions.countByAssignmentIdAndStudentId(closed.getId(), stu.getId())).isZero();
    }

    @Test
    void 지각_허용이면_지각으로_기록하고_지각_기한이_지나면_거부한다() {
        Assignment lateOk = data.assignment(course, prof, Duration.ofHours(-1), true, Duration.ofDays(1));
        var r = submissionService.submit(lateOk.getId(), stu, List.of(text("a.txt", "x")), null);
        assertThat(r.late()).isTrue();

        Assignment lateExpired = data.assignment(course, prof, Duration.ofHours(-5), true, Duration.ofHours(-1));
        assertThatThrownBy(() -> submissionService.submit(lateExpired.getId(), stu, List.of(text("a.txt", "x")), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("지각");
    }

    @Test
    void 마감_전_제출은_지각이_아니다() {
        assertThat(submissionService.submit(open.getId(), stu, List.of(text("a.txt", "x")), null).late()).isFalse();
    }

    @Test
    void 수강하지_않은_학생은_제출할수_없고_교수는_제출할수_없다() {
        assertThatThrownBy(() -> submissionService.submit(open.getId(), data.student(), List.of(text("a.txt", "x")), null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> submissionService.submit(open.getId(), prof, List.of(text("a.txt", "x")), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 파일이_없거나_메모가_너무_길면_거부된다() {
        assertThatThrownBy(() -> submissionService.submit(open.getId(), stu, List.of(), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> submissionService.submit(open.getId(), stu, List.of(text("a.txt", "x")), "가".repeat(501)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 다른_학생과_다른_교수는_제출물을_볼수_없다() {
        var r = submissionService.submit(open.getId(), stu, List.of(text("a.txt", "secret")), null);
        AppUser classmate = data.student();
        courseService.join(classmate, course.getInviteCode());
        AppUser otherProf = data.professor();

        assertThat(submissionService.view(r.id(), null, stu)).isNotNull();
        assertThat(submissionService.view(r.id(), null, prof)).isNotNull();
        assertThatThrownBy(() -> submissionService.view(r.id(), null, classmate)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> submissionService.view(r.id(), null, otherProf)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> submissionService.history(open.getId(), stu.getId(), classmate))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> submissionService.history(open.getId(), stu.getId(), otherProf))
                .isInstanceOf(ForbiddenException.class);

        Long fileId = submissionService.view(r.id(), null, stu).selectedFileId();
        assertThat(submissionService.download(fileId, stu).bytes()).isEqualTo("secret".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> submissionService.download(fileId, classmate)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> submissionService.download(fileId, otherProf)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void zip_은_풀려서_여러_파일로_저장되고_경로가_보존된다() throws Exception {
        byte[] z = zip(StandardCharsets.UTF_8,
                new String[]{"src/Main.java", "class Main {}\n"},
                new String[]{"src/util/Helper.java", "class Helper {}\n"},
                new String[]{"README.md", "# 안내\n"},
                new String[]{"__MACOSX/._README.md", "junk"},
                new String[]{".DS_Store", "junk"});
        var r = submissionService.submit(open.getId(), stu, List.of(new Upload("hw1.zip", z)), "zip 제출");

        var view = submissionService.view(r.id(), null, stu);
        assertThat(view.tree()).filteredOn(n -> !n.dir()).extracting(SubmissionService.FileNode::path)
                .containsExactly("README.md", "src/Main.java", "src/util/Helper.java");
        assertThat(view.tree()).filteredOn(SubmissionService.FileNode::dir).extracting(SubmissionService.FileNode::path)
                .containsExactly("src", "src/util");
    }

    @Test
    void zip_안의_위험한_경로는_거부된다() throws Exception {
        for (String evil : List.of("../evil.txt", "a/../../evil.txt", "/etc/passwd", "C:/Windows/evil.txt", "..\\evil.txt")) {
            byte[] z = zip(StandardCharsets.UTF_8, new String[]{evil, "x"});
            assertThatThrownBy(() -> submissionService.submit(open.getId(), stu, List.of(new Upload("e.zip", z)), null))
                    .as(evil).isInstanceOf(BusinessException.class).hasMessageContaining("경로");
        }
        assertThat(submissions.countByAssignmentIdAndStudentId(open.getId(), stu.getId())).isZero();
    }

    @Test
    void 한글_파일명이_CP949_인_zip_도_읽는다() throws Exception {
        byte[] z = zip(Charset.forName("MS949"), new String[]{"소스/메인.java", "class 메인 {}\n"});
        var r = submissionService.submit(open.getId(), stu, List.of(new Upload("과제.zip", z)), null);
        assertThat(submissionService.view(r.id(), null, stu).selectedPath()).isEqualTo("소스/메인.java");
    }

    @Test
    void 너무_큰_파일과_zip_폭탄은_거부된다() throws Exception {
        byte[] big = new byte[6 * 1024 * 1024];   // 한 파일 최대 5MB
        assertThatThrownBy(() -> submissionService.submit(open.getId(), stu, List.of(new Upload("big.txt", big)), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("큽니다");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("zeros.txt"));
            z.write(big);   // 압축하면 매우 작지만 풀면 6MB
            z.closeEntry();
        }
        assertThat(bos.size()).isLessThan(100_000);
        assertThatThrownBy(() -> submissionService.submit(open.getId(), stu, List.of(new Upload("bomb.zip", bos.toByteArray())), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("큽니다");
    }

    @Test
    void 바이너리는_텍스트로_저장하지_않고_CP949_텍스트는_복원한다() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0};
        byte[] cp949 = "// 한글 주석\nint x = 1;\n".getBytes("MS949");
        var r = submissionService.submit(open.getId(), stu,
                List.of(new Upload("img.png", png), new Upload("hello.c", cp949)), null);

        var view = submissionService.view(r.id(), null, stu);
        assertThat(view.selectedPath()).isEqualTo("hello.c");          // 바이너리 대신 텍스트 파일이 먼저 선택
        assertThat(view.selectedLines()).containsExactly("// 한글 주석", "int x = 1;");
        assertThat(view.selectedCharset()).isEqualToIgnoringCase("MS949");
        var pngNode = view.tree().stream().filter(n -> n.name().equals("img.png")).findFirst().orElseThrow();
        assertThat(pngNode.binary()).isTrue();
        var pngView = submissionService.view(r.id(), pngNode.id(), stu);
        assertThat(pngView.selectedBinary()).isTrue();
        assertThat(pngView.selectedLines()).isEmpty();
    }

    @Test
    void 저장_파일명은_UUID이고_사용자_파일명은_디스크에_쓰지_않는다() throws Exception {
        submissionService.submit(open.getId(), stu, List.of(text("../../내파일 name.txt", "x")), null);
        Path dir = Path.of("./target/test-uploads").toAbsolutePath().normalize();
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .allMatch(n -> n.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }
        // 경로 성분은 제거되고 파일 이름만 남는다
        var r = submissions.findTopByAssignmentIdAndStudentIdOrderByVersionNoDesc(open.getId(), stu.getId()).orElseThrow();
        assertThat(submissionService.view(r.getId(), null, stu).selectedPath()).isEqualTo("내파일 name.txt");
    }

    @Test
    void 버전_이력은_직전_버전_대비_변경_파일_수를_보여준다() {
        submissionService.submit(open.getId(), stu, List.of(text("a.txt", "1"), text("b.txt", "1")), null);
        submissionService.submit(open.getId(), stu, List.of(text("a.txt", "2"), text("c.txt", "1")), null);

        var rows = submissionService.history(open.getId(), stu.getId(), stu).versions();
        assertThat(rows).extracting(SubmissionService.VersionRow::versionNo).containsExactly(2, 1);   // 최신이 위
        var v2 = rows.get(0);
        assertThat(v2.added()).isEqualTo(1);      // c.txt
        assertThat(v2.modified()).isEqualTo(1);   // a.txt
        assertThat(v2.removed()).isEqualTo(1);    // b.txt
        assertThat(rows.get(1).added()).isEqualTo(2);
    }

    @Test
    void 교수_제출현황은_학생별_최신_버전과_미제출자를_보여준다() {
        AppUser stu2 = data.student();
        courseService.join(stu2, course.getInviteCode());
        submissionService.submit(open.getId(), stu, List.of(text("a.txt", "1")), null);
        submissionService.submit(open.getId(), stu, List.of(text("a.txt", "2")), null);

        var status = submissionService.statusFor(open.getId(), prof);
        assertThat(status).hasSize(2);
        var submitted = status.stream().filter(s -> s.studentId().equals(stu.getId())).findFirst().orElseThrow();
        var missing = status.stream().filter(s -> s.studentId().equals(stu2.getId())).findFirst().orElseThrow();
        assertThat(submitted.latestVersion()).isEqualTo(2);
        assertThat(submitted.versions()).isEqualTo(2);
        assertThat(missing.submitted()).isFalse();
        assertThatThrownBy(() -> submissionService.statusFor(open.getId(), stu)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 학생_과제_목록에_제출_완료와_지각_제출이_표시된다() {
        Assignment lateOk = data.assignment(course, prof, Duration.ofHours(-1), true, Duration.ofDays(1));
        submissionService.submit(open.getId(), stu, List.of(text("a.txt", "x")), null);
        submissionService.submit(lateOk.getId(), stu, List.of(text("a.txt", "x")), null);

        var rows = courseService.detail(course.getId(), stu).assignments();
        var openRow = rows.stream().filter(r -> r.id().equals(open.getId())).findFirst().orElseThrow();
        var lateRow = rows.stream().filter(r -> r.id().equals(lateOk.getId())).findFirst().orElseThrow();
        assertThat(openRow.state()).isEqualTo("SUBMITTED");
        assertThat(openRow.myVersions()).isEqualTo(1);
        assertThat(lateRow.state()).isEqualTo("LATE");
    }
}
