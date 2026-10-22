package com.submithub.service;

import com.submithub.core.diff.DiffLine;
import com.submithub.core.diff.DiffOptions;
import com.submithub.core.diff.DiffResult;
import com.submithub.core.diff.MyersDiff;
import com.submithub.core.diff.SideBySide;
import com.submithub.core.text.LineSplitter;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** 같은 학생·같은 과제의 임의의 두 버전을 파일별로 비교한다 (Myers diff). */
@Service
public class DiffService {

    static final int CONTEXT = 3;

    /** 파일 하나의 변경 요약. status: ADDED / REMOVED / MODIFIED / UNCHANGED */
    public record FileChange(String path, String status, boolean binary, int added, int removed) {
    }

    /** 통합 보기 한 줄. kind: " " 같음, "+" 추가, "-" 삭제, "~" 접힌 구간 (SpEL 에서 비교하기 쉽게 문자열) */
    public record UnifiedRow(String kind, int oldNo, int newNo, String text, int skipped) {
    }

    public record DiffView(Long assignmentId, String assignmentTitle, Long courseId, Long studentId,
                           String studentName, Long fromId, int fromVersion, Long toId, int toVersion,
                           List<SubmissionService.VersionLink> versions, List<FileChange> changes,
                           String selectedPath, boolean selectedBinary, Long toFileId,
                           String mode, boolean ignoreWhitespace, boolean fold, String summary,
                           List<SideBySide.Row> splitRows, List<UnifiedRow> unifiedRows,
                           Map<Integer, List<ReviewService.CommentThread>> threadsByNewLine) {
    }

    private final SubmissionRepository submissions;
    private final AccessService access;
    private final ReviewService reviews;

    public DiffService(SubmissionRepository submissions, AccessService access, ReviewService reviews) {
        this.submissions = submissions;
        this.access = access;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public DiffView compare(Long assignmentId, Long studentId, Long fromId, Long toId, String path, String mode,
                            boolean ignoreWhitespace, boolean fold, AppUser viewer) {
        access.studentHistory(assignmentId, studentId, viewer);
        List<Submission> all = submissions.findByAssignmentIdAndStudentIdOrderByVersionNoAsc(assignmentId, studentId);
        if (all.isEmpty()) {
            throw new NotFoundException("제출 내역이 없습니다.");
        }
        Submission to = pick(all, toId, all.get(all.size() - 1));
        Submission from = pick(all, fromId, all.size() >= 2 ? all.get(all.size() - 2) : all.get(0));
        String viewMode = "unified".equals(mode) ? "unified" : "split";

        Map<String, SubmissionFile> a = byPath(from);
        Map<String, SubmissionFile> b = byPath(to);
        List<FileChange> changes = new ArrayList<>();
        for (String p : new TreeSet<>(union(a, b))) {
            changes.add(change(p, a.get(p), b.get(p), ignoreWhitespace));
        }

        String selected = path;
        if (selected == null || !union(a, b).contains(selected)) {
            selected = changes.stream().filter(c -> !c.status().equals("UNCHANGED")).map(FileChange::path)
                    .findFirst().orElse(changes.isEmpty() ? null : changes.get(0).path());
        }

        List<SideBySide.Row> split = List.of();
        List<UnifiedRow> unified = List.of();
        boolean binary = false;
        String summary = "";
        Long toFileId = null;
        Map<Integer, List<ReviewService.CommentThread>> threads = Map.of();
        if (selected != null) {
            SubmissionFile fa = a.get(selected);
            SubmissionFile fb = b.get(selected);
            binary = (fa != null && fa.isBinary()) || (fb != null && fb.isBinary());
            if (fb != null) {
                toFileId = fb.getId();
            }
            if (!binary) {
                DiffResult d = MyersDiff.diff(text(fa), text(fb),
                        ignoreWhitespace ? DiffOptions.IGNORE_WHITESPACE : DiffOptions.DEFAULT);
                summary = d.summary();
                List<SideBySide.Row> rows = SideBySide.build(d);
                split = fold ? SideBySide.fold(rows, CONTEXT) : rows;
                unified = unified(d, fold);
                if (fb != null) {
                    threads = ReviewService.byEndLine(reviews.threadsUnchecked(fb.getId()));
                }
            }
        }

        List<SubmissionService.VersionLink> links = all.stream()
                .map(s -> new SubmissionService.VersionLink(s.getId(), s.getVersionNo(), s.getId().equals(to.getId())))
                .toList();
        return new DiffView(assignmentId, to.getAssignment().getTitle(), to.getAssignment().getCourse().getId(),
                studentId, to.getStudent().getName(), from.getId(), from.getVersionNo(), to.getId(),
                to.getVersionNo(), links, changes, selected, binary, toFileId, viewMode, ignoreWhitespace, fold,
                summary, split, unified, threads);
    }

    private static Submission pick(List<Submission> all, Long id, Submission fallback) {
        if (id == null) {
            return fallback;
        }
        return all.stream().filter(s -> s.getId().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException("같은 과제의 같은 학생의 버전만 비교할 수 있습니다."));
    }

    private static Map<String, SubmissionFile> byPath(Submission s) {
        Map<String, SubmissionFile> map = new TreeMap<>();
        for (SubmissionFile f : s.getFiles()) {
            map.put(f.getPath(), f);
        }
        return map;
    }

    private static java.util.Set<String> union(Map<String, SubmissionFile> a, Map<String, SubmissionFile> b) {
        java.util.Set<String> all = new TreeSet<>(a.keySet());
        all.addAll(b.keySet());
        return all;
    }

    private static String text(SubmissionFile f) {
        return f == null || f.getContentText() == null ? "" : f.getContentText();
    }

    private static FileChange change(String path, SubmissionFile a, SubmissionFile b, boolean ignoreWs) {
        if (a == null) {
            return new FileChange(path, "ADDED", b.isBinary(), b.isBinary() ? 0 : LineSplitter.split(text(b)).size(), 0);
        }
        if (b == null) {
            return new FileChange(path, "REMOVED", a.isBinary(), 0, a.isBinary() ? 0 : LineSplitter.split(text(a)).size());
        }
        if (a.getSha256().equals(b.getSha256())) {
            return new FileChange(path, "UNCHANGED", a.isBinary(), 0, 0);
        }
        if (a.isBinary() || b.isBinary()) {
            return new FileChange(path, "MODIFIED", true, 0, 0);
        }
        DiffResult d = MyersDiff.diff(text(a), text(b), ignoreWs ? DiffOptions.IGNORE_WHITESPACE : DiffOptions.DEFAULT);
        // 공백만 바뀐 파일을 "공백 무시"로 보면 변경 없음으로 취급
        return new FileChange(path, d.hasChanges() ? "MODIFIED" : "UNCHANGED", false, d.addedCount(), d.removedCount());
    }

    /** 통합(unified) 보기 행 만들기. fold 이면 변경 줄 앞뒤 CONTEXT 줄만 남기고 접는다 */
    static List<UnifiedRow> unified(DiffResult d, boolean fold) {
        List<DiffLine> lines = d.lines();
        boolean[] keep = new boolean[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            if (!fold || lines.get(i).type() != com.submithub.core.diff.DiffType.EQUAL) {
                for (int j = Math.max(0, i - (fold ? CONTEXT : 0)); j <= Math.min(lines.size() - 1, i + (fold ? CONTEXT : 0)); j++) {
                    keep[j] = true;
                }
            }
        }
        List<UnifiedRow> rows = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < lines.size(); i++) {
            DiffLine l = lines.get(i);
            if (!keep[i]) {
                skipped++;
                continue;
            }
            if (skipped > 0) {
                rows.add(new UnifiedRow("~", 0, 0, "", skipped));
                skipped = 0;
            }
            String kind = switch (l.type()) {
                case EQUAL -> " ";
                case ADDED -> "+";
                case REMOVED -> "-";
            };
            rows.add(new UnifiedRow(kind, l.oldLineNo(), l.newLineNo(), l.text(), 0));
        }
        if (skipped > 0) {
            rows.add(new UnifiedRow("~", 0, 0, "", skipped));
        }
        return rows;
    }
}
