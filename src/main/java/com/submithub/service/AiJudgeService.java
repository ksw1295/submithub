package com.submithub.service;

import com.submithub.ai.AiJudgeProvider;
import com.submithub.ai.AiJudgeProvider.AiJudgeException;
import com.submithub.ai.AiJudgeProvider.JudgeRequest;
import com.submithub.ai.AiJudgeProvider.JudgeResult;
import com.submithub.core.diff.DiffOptions;
import com.submithub.core.diff.MyersDiff;
import com.submithub.core.diff.SideBySide;
import com.submithub.core.text.LineSplitter;
import com.submithub.domain.AiDecision;
import com.submithub.domain.AiJudgement;
import com.submithub.domain.AiJudgementRepository;
import com.submithub.domain.AiVerdict;
import com.submithub.domain.Assignment;
import com.submithub.domain.Course;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.ReviewCommentRepository;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionFileRepository;
import com.submithub.security.AppUser;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 반영 판정.
 * LineTracker 가 MODIFIED("줄이 바뀜")로 본 코멘트에 대해, 바뀐 코드가 코멘트를 실제로 해결했는지 LLM 에게 묻는다.
 *
 * - 학생 코드가 외부로 나가므로 강의 설정(aiJudgeEnabled, 기본 꺼짐)이 켜져 있고 API 키가 있을 때만 동작한다.
 * - LLM 호출은 별도 스레드에서 하고, 호출 중에는 DB 트랜잭션을 잡지 않는다.
 * - 결과는 (코멘트, 판정 시점의 최신 버전) 단위로 저장해 재사용한다. 실패한 행은 다시 실행하면 다시 시도한다.
 * - 어떤 실패도 제출·화면 동작에 영향을 주지 않는다 ("판정 실패"로 저장하고 끝).
 */
@Service
public class AiJudgeService {

    private static final Logger log = LoggerFactory.getLogger(AiJudgeService.class);

    /** 코멘트 줄 앞뒤로 함께 보내는 줄 수 */
    static final int CONTEXT = 8;

    private record Task(Long commentId, int versionNo, JudgeRequest request) {
    }

    private final AiJudgeProvider provider;
    private final FeedbackService feedback;
    private final AiJudgementRepository judgements;
    private final ReviewCommentRepository comments;
    private final SubmissionFileRepository files;
    private final AssignmentRepository assignments;
    private final AccessService access;
    private final TransactionTemplate tx;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ai-judge");
        t.setDaemon(true);
        return t;
    });

    public AiJudgeService(AiJudgeProvider provider, FeedbackService feedback, AiJudgementRepository judgements,
                          ReviewCommentRepository comments, SubmissionFileRepository files,
                          AssignmentRepository assignments, AccessService access, PlatformTransactionManager txManager) {
        this.provider = provider;
        this.feedback = feedback;
        this.judgements = judgements;
        this.comments = comments;
        this.files = files;
        this.assignments = assignments;
        this.access = access;
        this.tx = new TransactionTemplate(txManager);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** 이 서버에서 AI 를 쓸 수 있는지 (키가 있는지). 없으면 화면에서 관련 기능을 숨긴다 */
    public boolean available() {
        return provider.available();
    }

    /** 강의에서 AI 판정을 켰고 서버에서도 쓸 수 있는지 */
    @Transactional(readOnly = true)
    public boolean enabledForAssignment(Long assignmentId) {
        return available() && assignments.findById(assignmentId)
                .map(a -> a.getCourse().isAiJudgeEnabled()).orElse(false);
    }

    /** 강의 설정 변경 (강의를 연 교수만). 서버에 키가 없으면 켤 수 없다 */
    @Transactional
    public void setEnabled(Long courseId, boolean enabled, AppUser professor) {
        Course course = access.courseOfProfessor(courseId, professor);
        if (enabled && !available()) {
            throw new BusinessException("이 서버에는 AI API 키가 설정되어 있지 않아 AI 판정을 켤 수 없습니다.");
        }
        course.setAiJudgeEnabled(enabled);
    }

    /** 교수가 버튼으로 요청: 한 학생의 한 과제에 대해 비동기로 판정한다. 반환값은 새로 판정한 코멘트 수 */
    public CompletableFuture<Integer> request(Long assignmentId, Long studentId, AppUser professor) {
        Boolean enabled = tx.execute(s -> {
            Assignment a = access.assignmentOfProfessor(assignmentId, professor);
            access.studentHistory(assignmentId, studentId, professor);
            return a.getCourse().isAiJudgeEnabled();
        });
        if (!available() || !Boolean.TRUE.equals(enabled)) {
            throw new BusinessException("이 강의에서는 AI 판정을 사용할 수 없습니다. 강의 화면에서 AI 판정을 켜세요.");
        }
        return run(assignmentId, studentId);
    }

    /** 새 버전이 제출된 뒤 호출. 설정이 꺼져 있거나 키가 없으면 아무것도 하지 않는다. 절대 예외를 던지지 않는다 */
    public void onNewVersion(Long assignmentId, Long studentId) {
        try {
            if (Boolean.TRUE.equals(tx.execute(s -> enabledForAssignment(assignmentId)))) {
                run(assignmentId, studentId);
            }
        } catch (RuntimeException e) {
            log.warn("AI 판정 예약 실패 (제출에는 영향 없음)", e);
        }
    }

    private CompletableFuture<Integer> run(Long assignmentId, Long studentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return judgeStudent(assignmentId, studentId);
            } catch (RuntimeException e) {
                log.warn("AI 판정 중 오류", e);
                return 0;
            }
        }, executor);
    }

    /**
     * 동기 실행 (비동기 실행과 테스트가 함께 쓴다). 새로 판정하거나 다시 시도한 코멘트 수를 돌려준다.
     */
    public int judgeStudent(Long assignmentId, Long studentId) {
        List<Task> tasks = tx.execute(s -> collect(assignmentId, studentId));
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int done = 0;
        for (Task t : tasks) {
            JudgeResult result;
            try {
                result = provider.judge(t.request());
            } catch (AiJudgeException e) {
                result = new JudgeResult(AiVerdict.FAILED, e.getMessage(), null);
            } catch (RuntimeException e) {
                log.warn("AI 판정 예외", e);
                result = new JudgeResult(AiVerdict.FAILED, "AI 판정 중 오류가 발생했습니다.", null);
            }
            save(t, result);
            done++;
        }
        return done;
    }

    private void save(Task t, JudgeResult r) {
        try {
            tx.executeWithoutResult(s -> {
                var existing = judgements.findByCommentIdAndVersionNo(t.commentId(), t.versionNo());
                if (existing.isPresent()) {
                    // 이미 성공한 판정(다른 요청이 먼저 끝낸 경우)은 덮어쓰지 않는다
                    if (existing.get().getVerdict() == AiVerdict.FAILED) {
                        existing.get().setResult(r.verdict(), r.reason(), r.model());
                    }
                } else {
                    judgements.save(new AiJudgement(comments.getReferenceById(t.commentId()), t.versionNo(),
                            r.verdict(), r.reason(), r.model()));
                }
            });
        } catch (DataIntegrityViolationException e) {
            log.debug("같은 코멘트·버전 판정이 동시에 저장됨", e);
        }
    }

    /** 판정이 필요한 코멘트(MODIFIED 이고 이번 최신 버전에 대한 성공한 판정이 없는 것)와 보낼 코드 조각을 모은다 */
    private List<Task> collect(Long assignmentId, Long studentId) {
        var view = feedback.computeUnchecked(assignmentId, studentId);
        List<Task> tasks = new ArrayList<>();
        for (var item : view.items()) {
            if (!item.status().equals("MODIFIED") || item.latestFileId() == null) {
                continue;
            }
            var existing = judgements.findByCommentIdAndVersionNo(item.commentId(), item.latestVersion());
            if (existing.isPresent() && existing.get().getVerdict() != AiVerdict.FAILED) {
                continue;     // 같은 코멘트+버전은 재사용
            }
            SubmissionFile oldFile = files.findById(item.commentFileId()).orElse(null);
            SubmissionFile newFile = files.findById(item.latestFileId()).orElse(null);
            if (oldFile == null || newFile == null || oldFile.isBinary() || newFile.isBinary()) {
                continue;
            }
            tasks.add(new Task(item.commentId(), item.latestVersion(),
                    request(item.body(), item.path(), oldFile.getContentText(), newFile.getContentText(),
                            item.startLine(), item.endLine())));
        }
        return tasks;
    }

    /** 이전 코드에서 코멘트 줄 주변, 새 코드에서 그 줄이 옮겨 간 곳 주변을 잘라 요청을 만든다 */
    static JudgeRequest request(String body, String path, String oldText, String newText, int start, int end) {
        List<String> oldLines = LineSplitter.split(oldText);
        List<String> newLines = LineSplitter.split(newText);
        int[] range = newRange(oldText, newText, start, end);

        int os = Math.max(1, start - CONTEXT);
        int oe = Math.min(oldLines.size(), end + CONTEXT);
        int ns = Math.max(1, range[0] - CONTEXT);
        int ne = Math.min(newLines.size(), range[1] + CONTEXT);
        return new JudgeRequest(body, path,
                os, oldLines.subList(os - 1, Math.max(os - 1, oe)),
                ns, newLines.subList(ns - 1, Math.max(ns - 1, ne)));
    }

    /** 이전 코드의 start~end 줄이 새 코드의 어느 줄 범위로 옮겨 갔는지 (diff 로 짝지은 줄 기준) */
    static int[] newRange(String oldText, String newText, int start, int end) {
        var rows = SideBySide.build(MyersDiff.diff(oldText, newText, DiffOptions.IGNORE_WHITESPACE));
        int min = Integer.MAX_VALUE;
        int max = 0;
        int lastRight = 1;
        for (var row : rows) {
            if (row.leftLineNo() >= start && row.leftLineNo() <= end && row.rightLineNo() > 0) {
                min = Math.min(min, row.rightLineNo());
                max = Math.max(max, row.rightLineNo());
            }
            if (row.rightLineNo() > 0 && (row.leftLineNo() == 0 || row.leftLineNo() < start)) {
                lastRight = row.rightLineNo();
            }
        }
        if (max == 0) {          // 짝지어진 줄이 없으면 가장 가까운 앞쪽 줄을 기준으로 한다
            return new int[]{lastRight, lastRight};
        }
        return new int[]{min, max};
    }

    /** 교수가 AI 판정을 수락하거나 무시한다 (해당 강의 교수만) */
    @Transactional
    public void decide(Long judgementId, AiDecision decision, AppUser professor) {
        AiJudgement j = judgements.findById(judgementId).orElseThrow(() -> new NotFoundException("AI 판정을 찾을 수 없습니다."));
        if (!professor.isProfessor()) {
            throw new ForbiddenException("교수만 AI 판정을 수락하거나 무시할 수 있습니다.");
        }
        access.courseOfProfessor(j.getComment().getSubmissionFile().getSubmission().getAssignment().getCourse().getId(), professor);
        if (j.getVerdict() == AiVerdict.FAILED) {
            throw new BusinessException("판정에 실패한 항목은 수락할 수 없습니다. 다시 실행하세요.");
        }
        j.decide(decision);
    }
}
