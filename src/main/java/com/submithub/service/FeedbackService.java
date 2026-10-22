package com.submithub.service;

import com.submithub.core.review.LineTracker;
import com.submithub.domain.ReviewComment;
import com.submithub.domain.ReviewCommentRepository;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 피드백 반영 추적.
 * 이전 버전에 달린 교수의 코멘트가 최신 버전까지 어떻게 되었는지 LineTracker 로 따라가서 판정한다.
 * 결과는 저장하지 않고 조회할 때 계산한다 (제출된 버전의 텍스트는 바뀌지 않는다).
 */
@Service
public class FeedbackService {

    /** 판정: PENDING(코멘트가 최신 버전에 달려 아직 고칠 기회 없음) / UNCHANGED(미반영) / MODIFIED(반영됨) / DELETED(삭제됨) */
    public record TrackedComment(Long commentId, int commentVersion, Long commentSubmissionId, Long commentFileId,
                                 String path, int startLine, int endLine, String body, String status,
                                 int latestStart, int latestEnd, int changedAtVersion, int latestVersion,
                                 Long latestSubmissionId, Long latestFileId, boolean markedResolved) {
        /** 학생이 아직 고쳐야 하는 피드백인가 */
        public boolean remaining() {
            return status.equals("UNCHANGED") || status.equals("PENDING");
        }
    }

    /** ratePercent: 반영률(%) = (반영됨+삭제됨) / (고칠 기회가 있었던 코멘트). 대상이 없으면 -1 */
    public record FeedbackSummary(int total, int tracked, int unchanged, int modified, int deleted, int pending,
                                  int ratePercent) {
        public int remaining() {
            return unchanged + pending;
        }

        public boolean hasRate() {
            return ratePercent >= 0;
        }

        /**
         * 화면에 보이는 반영률 표기 (과제 제출 현황·통계 화면 공용).
         * 반영률이 있으면 "75%", 새 코멘트만 있으면 "새 코멘트 1개", 코멘트가 없으면 "-"
         */
        public String rateLabel() {
            if (hasRate()) {
                return ratePercent + "%";
            }
            return pending > 0 ? "새 코멘트 " + pending + "개" : "-";
        }

        /** 반영률 옆의 보조 설명. 반영률이 있을 때만: "미반영 1 / 추적 4" (+ 새 코멘트가 더 있으면 함께) */
        public String rateDetail() {
            if (!hasRate()) {
                return "";
            }
            String text = "미반영 " + unchanged + " / 추적 " + tracked;
            return pending > 0 ? text + ", 새 코멘트 " + pending + "개" : text;
        }

        public static FeedbackSummary empty() {
            return of(List.of());
        }

        static FeedbackSummary of(List<TrackedComment> items) {
            int unchanged = 0, modified = 0, deleted = 0, pending = 0;
            for (TrackedComment c : items) {
                switch (c.status()) {
                    case "UNCHANGED" -> unchanged++;
                    case "MODIFIED" -> modified++;
                    case "DELETED" -> deleted++;
                    default -> pending++;
                }
            }
            int tracked = unchanged + modified + deleted;
            int rate = tracked == 0 ? -1 : (int) Math.round(100.0 * (modified + deleted) / tracked);
            return new FeedbackSummary(items.size(), tracked, unchanged, modified, deleted, pending, rate);
        }
    }

    public record FeedbackView(List<TrackedComment> items, FeedbackSummary summary) {
    }

    private final SubmissionRepository submissions;
    private final ReviewCommentRepository comments;
    private final AccessService access;

    public FeedbackService(SubmissionRepository submissions, ReviewCommentRepository comments, AccessService access) {
        this.submissions = submissions;
        this.comments = comments;
        this.access = access;
    }

    /** 한 학생의 한 과제: 본인 또는 그 강의 교수만 */
    @Transactional(readOnly = true)
    public FeedbackView forStudent(Long assignmentId, Long studentId, AppUser viewer) {
        access.studentHistory(assignmentId, studentId, viewer);
        return computeUnchecked(assignmentId, studentId);
    }

    /** 교수: 과제의 학생별 반영 요약 (학생 ID → 요약). 코멘트가 없는 학생은 포함하지 않는다 */
    @Transactional(readOnly = true)
    public Map<Long, FeedbackSummary> summaries(Long assignmentId, AppUser professor) {
        access.assignmentOfProfessor(assignmentId, professor);
        List<Long> studentIds = comments.findRootsForAssignment(assignmentId).stream()
                .map(c -> c.getSubmissionFile().getSubmission().getStudent().getId()).distinct().toList();
        Map<Long, FeedbackSummary> result = new LinkedHashMap<>();
        for (Long id : studentIds) {
            result.put(id, computeUnchecked(assignmentId, id).summary());
        }
        return result;
    }

    /** 권한 검사가 끝난 호출용 */
    @Transactional(readOnly = true)
    public FeedbackView computeUnchecked(Long assignmentId, Long studentId) {
        List<Submission> subs = submissions.findByAssignmentIdAndStudentIdOrderByVersionNoAsc(assignmentId, studentId);
        List<ReviewComment> roots = comments.findRootsForStudent(assignmentId, studentId);
        if (subs.isEmpty() || roots.isEmpty()) {
            return new FeedbackView(List.of(), FeedbackSummary.of(List.of()));
        }
        Submission latest = subs.get(subs.size() - 1);

        // 버전별 경로별 텍스트 (바이너리/없는 파일은 빈 문자열: 파일이 지워진 것으로 본다)
        Map<Integer, Map<String, String>> texts = new HashMap<>();
        Map<Integer, Map<String, SubmissionFile>> fileOf = new HashMap<>();
        for (Submission s : subs) {
            Map<String, String> t = new HashMap<>();
            Map<String, SubmissionFile> f = new HashMap<>();
            for (SubmissionFile file : s.getFiles()) {
                t.put(file.getPath(), file.getContentText() == null ? "" : file.getContentText());
                f.put(file.getPath(), file);
            }
            texts.put(s.getVersionNo(), t);
            fileOf.put(s.getVersionNo(), f);
        }

        List<TrackedComment> items = new ArrayList<>();
        for (ReviewComment c : roots) {
            SubmissionFile cf = c.getSubmissionFile();
            int fromVersion = cf.getSubmission().getVersionNo();
            String path = cf.getPath();
            SubmissionFile latestFile = fileOf.get(latest.getVersionNo()).get(path);

            List<String> versions = new ArrayList<>();
            for (Submission s : subs) {
                if (s.getVersionNo() >= fromVersion) {
                    versions.add(texts.get(s.getVersionNo()).getOrDefault(path, ""));
                }
            }
            String status;
            int ls = 0, le = 0, changedAt = 0;
            if (versions.size() <= 1) {
                status = "PENDING";
            } else {
                LineTracker.Result r = LineTracker.track(versions, c.getStartLine(), c.getEndLine());
                status = r.status().name();
                if (r.status() == LineTracker.Status.UNCHANGED) {
                    ls = r.startLine();
                    le = r.endLine();
                } else {
                    changedAt = fromVersion + r.changedAtIndex();
                }
            }
            items.add(new TrackedComment(c.getId(), fromVersion, cf.getSubmission().getId(), cf.getId(), path,
                    c.getStartLine(), c.getEndLine(), c.getBody(), status, ls, le, changedAt,
                    latest.getVersionNo(), latest.getId(), latestFile == null ? null : latestFile.getId(),
                    c.isResolvedByStudent()));
        }
        return new FeedbackView(items, FeedbackSummary.of(items));
    }
}
