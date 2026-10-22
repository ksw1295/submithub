package com.submithub.service;

import com.submithub.core.text.LineSplitter;
import com.submithub.core.text.TextDecoder;
import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.Enrollment;
import com.submithub.domain.EnrollmentRepository;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.domain.User;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
import com.submithub.storage.FileStorage;
import com.submithub.storage.UploadProcessor;
import com.submithub.storage.UploadProcessor.Processed;
import com.submithub.storage.UploadProcessor.Upload;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SubmissionService {

    public record SubmitResult(Long id, int versionNo, boolean late) {
    }

    /** 버전 타임라인 한 줄. added/modified/removed 는 직전 버전과 비교한 파일 수 */
    public record VersionRow(Long id, int versionNo, String message, boolean late, LocalDateTime submittedAt,
                             int fileCount, long totalSize, int added, int modified, int removed) {
    }

    public record HistoryView(Long assignmentId, String assignmentTitle, Long courseId, Long studentId,
                              String studentName, List<VersionRow> versions) {
    }

    public record StudentStatus(Long studentId, String name, String studentNo, int versions,
                                Long latestSubmissionId, int latestVersion, LocalDateTime submittedAt,
                                boolean late) {
        public boolean submitted() {
            return latestSubmissionId != null;
        }
    }

    public record FileNode(Long id, String name, String path, int depth, boolean dir, long size, boolean binary) {
    }

    public record VersionLink(Long id, int versionNo, boolean current) {
    }

    public record SubmissionView(Long id, int versionNo, String message, boolean late, LocalDateTime submittedAt,
                                 Long assignmentId, String assignmentTitle, Long courseId, Long studentId,
                                 String studentName, List<FileNode> tree, Long selectedFileId,
                                 String selectedPath, List<String> selectedLines, boolean selectedBinary,
                                 String selectedCharset, long selectedSize, List<VersionLink> versions) {
    }

    public record Download(String filename, byte[] bytes) {
    }

    private record Prepared(String path, byte[] bytes, String sha256, String charset, String text) {
    }

    private final SubmissionRepository submissions;
    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final AccessService access;
    private final UploadProcessor processor;
    private final FileStorage storage;
    private final TransactionTemplate tx;

    public SubmissionService(SubmissionRepository submissions, AssignmentRepository assignments,
                             EnrollmentRepository enrollments, UserRepository users, AccessService access,
                             UploadProcessor processor, FileStorage storage, PlatformTransactionManager txManager) {
        this.submissions = submissions;
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.users = users;
        this.access = access;
        this.processor = processor;
        this.storage = storage;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * 제출: 파일(또는 zip)과 메모를 받아 새 버전을 INSERT 한다.
     * 버전 번호 부여는 과제 행 락(SELECT ... FOR UPDATE) 안에서 하고, 그래도 유니크 키가 충돌하면 한 번 재시도한다.
     */
    public SubmitResult submit(Long assignmentId, AppUser student, List<Upload> uploads, String message) {
        if (student.isProfessor()) {
            throw new ForbiddenException("학생만 제출할 수 있습니다.");
        }
        String memo = message == null ? null : message.strip();
        if (memo != null && memo.length() > 500) {
            throw new BusinessException("제출 메모는 500자 이하로 입력하세요.");
        }
        // 파일 검사·해시·문자집합 판별은 락을 잡기 전에 끝낸다
        List<Prepared> prepared = prepare(processor.process(uploads));
        try {
            return tx.execute(status -> doSubmit(assignmentId, student, prepared, memo));
        } catch (DataIntegrityViolationException e) {
            return tx.execute(status -> doSubmit(assignmentId, student, prepared, memo));
        }
    }

    private SubmitResult doSubmit(Long assignmentId, AppUser student, List<Prepared> prepared, String memo) {
        access.assignment(assignmentId, student);   // 수강 중인 강의의 과제인지
        Assignment assignment = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now();
        if (!assignment.isOpen(now)) {
            throw new BusinessException(assignment.isAllowLate()
                    ? "지각 제출 기한이 지나 제출할 수 없습니다."
                    : "마감되어 제출할 수 없습니다.");
        }
        int version = submissions.maxVersion(assignmentId, student.getId()) + 1;
        User owner = users.getReferenceById(student.getId());
        Submission submission = new Submission(assignment, owner, version, memo, assignment.isPastDue(now), now);

        List<String> written = new ArrayList<>();
        // 트랜잭션이 롤백되면 이미 디스크에 쓴 파일을 지운다
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    written.forEach(storage::deleteQuietly);
                }
            }
        });
        for (Prepared p : prepared) {
            String stored = storage.store(p.bytes());
            written.add(stored);
            submission.addFile(new SubmissionFile(submission, p.path(), p.bytes().length, p.sha256(),
                    p.charset(), p.text(), stored));
        }
        submissions.saveAndFlush(submission);
        return new SubmitResult(submission.getId(), version, submission.isLate());
    }

    private static List<Prepared> prepare(List<Processed> processed) {
        List<Prepared> list = new ArrayList<>();
        for (Processed p : processed) {
            String charset = null;
            String text = null;
            if (!TextDecoder.looksBinary(p.bytes())) {
                TextDecoder.Decoded d = TextDecoder.decode(p.bytes());
                charset = "x-windows-949".equals(d.charset().name()) ? "MS949" : d.charset().name();
                text = d.text();
            }
            list.add(new Prepared(p.path(), p.bytes(), sha256(p.bytes()), charset, text));
        }
        return list;
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 한 학생의 한 과제 제출 이력 (본인 또는 그 강의의 교수만) */
    @Transactional(readOnly = true)
    public HistoryView history(Long assignmentId, Long studentId, AppUser viewer) {
        Assignment a = access.studentHistory(assignmentId, studentId, viewer);
        User student = users.findById(studentId).orElseThrow(() -> new NotFoundException("학생을 찾을 수 없습니다."));
        List<Submission> list = submissions.findByAssignmentIdAndStudentIdOrderByVersionNoAsc(assignmentId, studentId);
        List<VersionRow> rows = new ArrayList<>();
        Map<String, String> previous = Map.of();
        for (Submission s : list) {
            Map<String, String> current = new HashMap<>();
            long total = 0;
            for (SubmissionFile f : s.getFiles()) {
                current.put(f.getPath(), f.getSha256());
                total += f.getSize();
            }
            int added = 0;
            int modified = 0;
            for (var e : current.entrySet()) {
                String before = previous.get(e.getKey());
                if (before == null) {
                    added++;
                } else if (!before.equals(e.getValue())) {
                    modified++;
                }
            }
            int removed = (int) previous.keySet().stream().filter(p -> !current.containsKey(p)).count();
            rows.add(new VersionRow(s.getId(), s.getVersionNo(), s.getMessage(), s.isLate(), s.getSubmittedAt(),
                    s.getFiles().size(), total, added, modified, removed));
            previous = current;
        }
        java.util.Collections.reverse(rows);   // 최신 버전이 위로
        return new HistoryView(a.getId(), a.getTitle(), a.getCourse().getId(), studentId, student.getName(), rows);
    }

    /** 교수: 과제의 학생별 최신 제출 현황 (미제출자 포함) */
    @Transactional(readOnly = true)
    public List<StudentStatus> statusFor(Long assignmentId, AppUser professor) {
        Assignment a = access.assignmentOfProfessor(assignmentId, professor);
        Map<Long, Submission> latest = new HashMap<>();
        for (Submission s : submissions.findLatestPerStudent(assignmentId)) {
            latest.put(s.getStudent().getId(), s);
        }
        List<StudentStatus> result = new ArrayList<>();
        for (Enrollment e : enrollments.findByCourseIdOrderByStudentNameAsc(a.getCourse().getId())) {
            User st = e.getStudent();
            Submission s = latest.get(st.getId());
            result.add(s == null
                    ? new StudentStatus(st.getId(), st.getName(), st.getStudentNo(), 0, null, 0, null, false)
                    : new StudentStatus(st.getId(), st.getName(), st.getStudentNo(), s.getVersionNo(), s.getId(),
                    s.getVersionNo(), s.getSubmittedAt(), s.isLate()));
        }
        return result;
    }

    /** 제출물 화면: 파일 트리 + 선택한 파일의 코드 */
    @Transactional(readOnly = true)
    public SubmissionView view(Long submissionId, Long fileId, AppUser viewer) {
        Submission s = access.submission(submissionId, viewer);
        List<SubmissionFile> files = s.getFiles();

        SubmissionFile selected = null;
        if (fileId != null) {
            selected = files.stream().filter(f -> f.getId().equals(fileId)).findFirst().orElse(null);
        }
        if (selected == null) {
            selected = files.stream().filter(f -> !f.isBinary()).findFirst()
                    .orElse(files.isEmpty() ? null : files.get(0));
        }

        List<VersionLink> versions = submissions
                .findByAssignmentIdAndStudentIdOrderByVersionNoAsc(s.getAssignment().getId(), s.getStudent().getId())
                .stream().map(v -> new VersionLink(v.getId(), v.getVersionNo(), v.getId().equals(s.getId()))).toList();

        return new SubmissionView(s.getId(), s.getVersionNo(), s.getMessage(), s.isLate(), s.getSubmittedAt(),
                s.getAssignment().getId(), s.getAssignment().getTitle(), s.getAssignment().getCourse().getId(),
                s.getStudent().getId(), s.getStudent().getName(), tree(files),
                selected == null ? null : selected.getId(),
                selected == null ? null : selected.getPath(),
                selected == null ? List.of() : LineSplitter.split(selected.getContentText()),
                selected != null && selected.isBinary(),
                selected == null ? null : selected.getCharset(),
                selected == null ? 0 : selected.getSize(), versions);
    }

    /** 경로 목록을 들여쓰기 깊이가 있는 평평한 트리 목록으로 바꾼다 */
    static List<FileNode> tree(List<SubmissionFile> files) {
        List<SubmissionFile> sorted = new ArrayList<>(files);
        sorted.sort(java.util.Comparator.comparing(SubmissionFile::getPath));
        List<FileNode> nodes = new ArrayList<>();
        Set<String> dirs = new HashSet<>();
        for (SubmissionFile f : sorted) {
            String[] seg = f.getPath().split("/");
            StringBuilder dir = new StringBuilder();
            for (int i = 0; i < seg.length - 1; i++) {
                dir.append(i == 0 ? "" : "/").append(seg[i]);
                if (dirs.add(dir.toString())) {
                    nodes.add(new FileNode(null, seg[i], dir.toString(), i, true, 0, false));
                }
            }
            nodes.add(new FileNode(f.getId(), seg[seg.length - 1], f.getPath(), seg.length - 1, false,
                    f.getSize(), f.isBinary()));
        }
        return nodes;
    }

    /** 원본 파일 내려받기 */
    @Transactional(readOnly = true)
    public Download download(Long fileId, AppUser viewer) {
        SubmissionFile f = access.file(fileId, viewer);
        String path = f.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new Download(name, storage.read(f.getStoragePath()));
    }

    /** 코멘트·diff 에서 쓰는 텍스트 (바이너리면 빈 문자열) */
    static String textOf(SubmissionFile f) {
        return f == null || f.getContentText() == null ? "" : f.getContentText();
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
