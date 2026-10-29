package com.submithub.service;

import com.submithub.domain.Assignment;
import com.submithub.domain.AssignmentRepository;
import com.submithub.domain.GradingConfig;
import com.submithub.domain.GradingConfigRepository;
import com.submithub.domain.GradingLanguage;
import com.submithub.domain.GradingResult;
import com.submithub.domain.GradingRun;
import com.submithub.domain.GradingRunRepository;
import com.submithub.domain.GradingStatus;
import com.submithub.domain.GradingTestCase;
import com.submithub.domain.GradingTestCaseRepository;
import com.submithub.domain.GradingVerdict;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.grading.DockerCommands;
import com.submithub.grading.GradingRunner;
import com.submithub.grading.GradingRunner.CaseOutcome;
import com.submithub.grading.GradingRunner.CaseStatus;
import com.submithub.grading.GradingRunner.GradingException;
import com.submithub.grading.GradingRunner.Outcome;
import com.submithub.grading.GradingRunner.Request;
import com.submithub.grading.OutputComparator;
import com.submithub.security.AppUser;
import com.submithub.storage.FileStorage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 코드 자동 채점.
 * 교수가 과제에 테스트 케이스와 실행 설정을 등록하면, 새 버전이 제출될 때마다 비동기로 채점한다:
 * 컴파일 → 케이스별 실행(격리 실행기) → 출력 비교(끝 공백·줄바꿈 정규화). 학생 코드는 GradingRunner(Docker)에서만 실행된다.
 * 실행기를 쓸 수 없으면 "채점 불가"로 기록하고 나머지 기능은 그대로 동작한다.
 */
@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    static final int MAX_CASES = 50;
    static final int MAX_TEXT = 20_000;
    static final int STORED_ACTUAL = 10_000;
    static final int STORED_ERROR = 4_000;
    private static final Pattern MAIN_FILE = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_./-]*$");

    // ---------- 화면용 DTO ----------

    public record ConfigInput(GradingLanguage language, String mainFile, int timeLimitSeconds, int memoryLimitMb) {
    }

    public record CaseInput(String name, String input, String expectedOutput, boolean publicCase, int points) {
    }

    public record TestCaseView(Long id, String name, boolean publicCase, int points, String input, String expected) {
    }

    public record ConfigView(boolean configured, GradingLanguage language, String mainFile, int timeLimitSeconds,
                             int memoryLimitMb, List<TestCaseView> cases, int totalPoints, boolean runnerAvailable,
                             String runnerName) {
    }

    /** 케이스 결과. 학생에게 비공개 케이스는 input/expected/actual/error 가 null 이다 */
    public record CaseView(String name, boolean publicCase, String verdict, int points, int awarded, long durationMs,
                           String input, String expected, String actual, String error) {
    }

    /** status: NONE(아직 채점 안 함) / QUEUED / RUNNING / DONE / UNAVAILABLE / ERROR */
    public record GradingView(String status, boolean compileOk, String compileMessage, int passed, int total,
                              int percent, int score, int totalPoints, String message, String runner,
                              List<CaseView> cases, boolean runnerAvailable) {
        public boolean done() {
            return status.equals("DONE");
        }
    }

    /** 제출 목록(이력)에 보여줄 한 줄 요약 */
    public record AutoGrade(String label, int percent, String status) {
    }

    public record TrendPoint(int versionNo, Long submissionId, String status, boolean compileOk, int passed, int total,
                             int percent) {
        public boolean graded() {
            return status.equals("DONE") && percent >= 0;
        }
    }

    /** 버전별 통과율 추이. delta 는 처음 채점된 버전 대비 가장 최근 채점된 버전의 통과율 변화(%p) */
    public record TrendView(List<TrendPoint> points, int delta, int graded) {
    }

    private record CaseSnapshot(Long id, String name, boolean publicCase, int points, String input, String expected) {
    }

    private record Prepared(Request request, List<CaseSnapshot> cases) {
    }

    private final GradingRunner runner;
    private final GradingConfigRepository configs;
    private final GradingTestCaseRepository cases;
    private final GradingRunRepository runs;
    private final SubmissionRepository submissions;
    private final AssignmentRepository assignments;
    private final FileStorage storage;
    private final AccessService access;
    private final TransactionTemplate tx;
    private final ExecutorService executor;

    @Autowired
    public GradingService(GradingRunner runner, GradingConfigRepository configs, GradingTestCaseRepository cases,
                          GradingRunRepository runs, SubmissionRepository submissions, AssignmentRepository assignments,
                          FileStorage storage, AccessService access, PlatformTransactionManager txManager,
                          @Value("${submithub.grading.max-concurrent-runs:2}") int maxConcurrentRuns) {
        this.runner = runner;
        this.configs = configs;
        this.cases = cases;
        this.runs = runs;
        this.submissions = submissions;
        this.assignments = assignments;
        this.storage = storage;
        this.access = access;
        this.tx = new TransactionTemplate(txManager);
        int concurrency = Math.max(1, maxConcurrentRuns);
        this.executor = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "grading");
            t.setDaemon(true);
            return t;
        });
    }

    public GradingService(GradingRunner runner, GradingConfigRepository configs, GradingTestCaseRepository cases,
                          GradingRunRepository runs, SubmissionRepository submissions, AssignmentRepository assignments,
                          FileStorage storage, AccessService access, PlatformTransactionManager txManager) {
        this(runner, configs, cases, runs, submissions, assignments, storage, access, txManager, 2);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** 이 서버에서 채점 실행기(Docker)를 쓸 수 있는지 */
    public boolean available() {
        return runner.available();
    }

    // ---------- 설정 (교수) ----------

    @Transactional(readOnly = true)
    public ConfigView config(Long assignmentId, AppUser professor) {
        access.assignmentOfProfessor(assignmentId, professor);
        var cfg = configs.findByAssignmentId(assignmentId).orElse(null);
        List<TestCaseView> list = cases.findByAssignmentIdOrderByOrderNoAscIdAsc(assignmentId).stream()
                .map(c -> new TestCaseView(c.getId(), c.getName(), c.isPublicCase(), c.getPoints(), c.getInput(), c.getExpectedOutput()))
                .toList();
        return new ConfigView(cfg != null, cfg == null ? GradingLanguage.JAVA : cfg.getLanguage(),
                cfg == null ? "Main.java" : cfg.getMainFile(), cfg == null ? 5 : cfg.getTimeLimitSeconds(),
                cfg == null ? 256 : cfg.getMemoryLimitMb(), list, list.stream().mapToInt(TestCaseView::points).sum(),
                runner.available(), runner.name());
    }

    @Transactional
    public void saveConfig(Long assignmentId, AppUser professor, ConfigInput in) {
        Assignment a = access.assignmentOfProfessor(assignmentId, professor);
        if (in.language() == null) {
            throw new BusinessException("언어를 선택하세요.");
        }
        String main = in.mainFile() == null ? "" : in.mainFile().trim();
        if (main.isEmpty() || main.length() > 200 || !MAIN_FILE.matcher(main).matches() || main.contains("..")
                || main.endsWith("/")) {
            throw new BusinessException("실행할 파일 경로가 올바르지 않습니다. (영문, 숫자, _ . / - 만 사용, 예: Main.java)");
        }
        if (!main.endsWith(in.language().getExtension())) {
            throw new BusinessException(in.language().getLabel() + " 실행 파일은 " + in.language().getExtension() + " 로 끝나야 합니다.");
        }
        if (in.language() == GradingLanguage.JAVA && !DockerCommands.validClassName(
                main.substring(main.lastIndexOf('/') + 1, main.length() - ".java".length()))) {
            throw new BusinessException("Java 실행 파일 이름이 클래스 이름으로 쓸 수 없는 형태입니다.");
        }
        if (in.timeLimitSeconds() < 1 || in.timeLimitSeconds() > 30) {
            throw new BusinessException("시간 제한은 1~30초 사이로 입력하세요.");
        }
        if (in.memoryLimitMb() < 64 || in.memoryLimitMb() > 512) {
            throw new BusinessException("메모리 제한은 64~512MB 사이로 입력하세요.");
        }
        configs.findByAssignmentId(assignmentId).ifPresentOrElse(
                c -> c.update(in.language(), main, in.timeLimitSeconds(), in.memoryLimitMb()),
                () -> configs.save(new GradingConfig(a, in.language(), main, in.timeLimitSeconds(), in.memoryLimitMb())));
    }

    @Transactional
    public Long addCase(Long assignmentId, AppUser professor, CaseInput in) {
        Assignment a = access.assignmentOfProfessor(assignmentId, professor);
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new BusinessException("케이스 이름을 100자 이하로 입력하세요.");
        }
        if ((in.input() != null && in.input().length() > MAX_TEXT)
                || (in.expectedOutput() != null && in.expectedOutput().length() > MAX_TEXT)) {
            throw new BusinessException("입력과 기대 출력은 각각 " + MAX_TEXT + "자 이하로 입력하세요.");
        }
        if (in.expectedOutput() == null || in.expectedOutput().isBlank()) {
            throw new BusinessException("기대 출력을 입력하세요.");
        }
        if (in.points() < 0 || in.points() > 1000) {
            throw new BusinessException("배점은 0~1000 사이로 입력하세요.");
        }
        long count = cases.countByAssignmentId(assignmentId);
        if (count >= MAX_CASES) {
            throw new BusinessException("테스트 케이스는 과제당 최대 " + MAX_CASES + "개입니다.");
        }
        return cases.save(new GradingTestCase(a, name, in.input() == null ? "" : in.input(), in.expectedOutput(),
                in.publicCase(), in.points(), (int) count + 1)).getId();
    }

    @Transactional
    public void deleteCase(Long assignmentId, Long caseId, AppUser professor) {
        access.assignmentOfProfessor(assignmentId, professor);
        GradingTestCase c = cases.findById(caseId).orElseThrow(() -> new NotFoundException("테스트 케이스를 찾을 수 없습니다."));
        if (!c.getAssignment().getId().equals(assignmentId)) {
            throw new NotFoundException("테스트 케이스를 찾을 수 없습니다.");
        }
        cases.delete(c);
    }

    // ---------- 채점 실행 ----------

    /** 새 버전이 제출된 뒤 호출된다. 설정이 없거나 실패해도 제출에는 영향이 없다 */
    public void onNewVersion(Long submissionId) {
        try {
            grade(submissionId);
        } catch (RuntimeException e) {
            log.warn("채점 예약 실패 (제출에는 영향 없음)", e);
        }
    }

    /** 교수가 한 제출을 다시 채점한다 */
    public CompletableFuture<Void> regrade(Long submissionId, AppUser professor) {
        tx.executeWithoutResult(s -> access.assignmentOfProfessor(access.submission(submissionId, professor).getAssignment().getId(), professor));
        return grade(submissionId);
    }

    /** 교수가 과제의 모든 제출(모든 버전)을 다시 채점한다. 반환값은 대상 제출 수 */
    public CompletableFuture<Integer> regradeAll(Long assignmentId, AppUser professor) {
        List<Long> ids = tx.execute(s -> {
            access.assignmentOfProfessor(assignmentId, professor);
            return submissions.findByAssignmentIdOrderBySubmittedAtAsc(assignmentId).stream().map(Submission::getId).toList();
        });
        if (!runner.available()) {
            throw new BusinessException("채점 실행기(Docker)를 사용할 수 없어 다시 채점할 수 없습니다.");
        }
        return CompletableFuture.supplyAsync(() -> {
            for (Long id : ids) {
                try {
                    execute(prepareRun(id));
                } catch (RuntimeException e) {
                    log.warn("재채점 중 오류 (submission {})", id, e);
                }
            }
            return ids.size();
        }, executor);
    }

    /** 채점을 예약한다 (비동기). 채점 대상이 아니면 바로 끝난 Future 를 돌려준다 */
    public CompletableFuture<Void> grade(Long submissionId) {
        Long runId = prepareRun(submissionId);
        if (runId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> execute(runId), executor);
    }

    /** 채점 기록(QUEUED)을 만든다. 채점 대상이 아니거나 실행기를 쓸 수 없으면 null */
    private Long prepareRun(Long submissionId) {
        // 채점 설정이 없는 과제는 실행기(Docker) 상태를 확인하지 않고 바로 끝낸다
        Boolean graded = tx.execute(s -> submissions.findById(submissionId)
                .map(sub -> configured(sub.getAssignment().getId())).orElse(false));
        if (!Boolean.TRUE.equals(graded)) {
            return null;
        }
        boolean up = runner.available();
        return tx.execute(s -> {
            Submission sub = submissions.findById(submissionId).orElse(null);
            if (sub == null) {
                return null;
            }
            GradingRun run = runs.findBySubmissionId(submissionId).orElseGet(() -> new GradingRun(sub));
            run.reset(GradingStatus.QUEUED, null);
            if (!up) {
                run.markFailed(GradingStatus.UNAVAILABLE,
                        "채점 실행기(Docker)를 사용할 수 없어 채점하지 못했습니다. Docker 를 켠 뒤 교수가 다시 채점할 수 있습니다.");
                runs.save(run);
                return null;
            }
            return runs.save(run).getId();
        });
    }

    /** 채점 한 건을 실행한다 (비동기 스레드에서 호출). 어떤 실패도 "채점 오류"로 기록하고 끝낸다 */
    void execute(Long runId) {
        if (runId == null) {
            return;
        }
        Prepared prepared;
        try {
            prepared = tx.execute(s -> load(runId));
        } catch (RuntimeException e) {
            log.warn("채점 준비 실패", e);
            fail(runId, "채점을 준비하지 못했습니다.");
            return;
        }
        Outcome outcome;
        try {
            outcome = runner.run(prepared.request());      // DB 트랜잭션 밖에서 실행
        } catch (GradingException e) {
            fail(runId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.warn("채점 실행 중 예외", e);
            fail(runId, "채점 중 오류가 발생했습니다.");
            return;
        }
        try {
            tx.executeWithoutResult(s -> finish(runId, prepared, outcome));
        } catch (RuntimeException e) {
            log.warn("채점 결과 저장 실패", e);
            fail(runId, "채점 결과를 저장하지 못했습니다.");
        }
    }

    private Prepared load(Long runId) {
        GradingRun run = runs.findById(runId).orElseThrow();
        Submission sub = run.getSubmission();
        Long assignmentId = sub.getAssignment().getId();
        GradingConfig cfg = configs.findByAssignmentId(assignmentId).orElseThrow();
        List<GradingTestCase> list = cases.findByAssignmentIdOrderByOrderNoAscIdAsc(assignmentId);

        Map<String, byte[]> files = new LinkedHashMap<>();
        for (SubmissionFile f : sub.getFiles()) {
            files.put(f.getPath(), storage.read(f.getStoragePath()));
        }
        List<CaseSnapshot> snaps = new ArrayList<>();
        List<GradingRunner.Case> inputs = new ArrayList<>();
        for (GradingTestCase c : list) {
            snaps.add(new CaseSnapshot(c.getId(), c.getName(), c.isPublicCase(), c.getPoints(), c.getInput(), c.getExpectedOutput()));
            inputs.add(new GradingRunner.Case(c.getId(), c.getInput()));
        }
        run.markRunning(runner.name());
        return new Prepared(new Request(cfg.getLanguage(), cfg.getMainFile(), files, inputs,
                cfg.getTimeLimitSeconds(), cfg.getMemoryLimitMb()), snaps);
    }

    private void finish(Long runId, Prepared prepared, Outcome outcome) {
        GradingRun run = runs.findById(runId).orElseThrow();
        List<GradingResult> results = new ArrayList<>();
        Map<Long, CaseOutcome> byId = new HashMap<>();
        for (CaseOutcome o : outcome.cases()) {
            byId.put(o.id(), o);
        }
        for (CaseSnapshot c : prepared.cases()) {
            GradingVerdict verdict;
            String actual = null;
            String error = null;
            long millis = 0;
            if (!outcome.compiled()) {
                verdict = GradingVerdict.COMPILE_ERROR;
            } else {
                CaseOutcome o = byId.get(c.id());
                if (o == null) {
                    verdict = GradingVerdict.RUNTIME_ERROR;
                    error = "실행 결과가 없습니다.";
                } else {
                    millis = o.durationMs();
                    actual = cut(o.stdout(), STORED_ACTUAL);
                    error = cut(o.stderr(), STORED_ERROR);
                    verdict = switch (o.status()) {
                        case TIMEOUT -> GradingVerdict.TIMEOUT;
                        case RUNTIME_ERROR -> GradingVerdict.RUNTIME_ERROR;
                        case OK -> OutputComparator.matches(c.expected(), o.stdout())
                                ? GradingVerdict.PASSED : GradingVerdict.FAILED;
                    };
                }
            }
            results.add(new GradingResult(run, c.name(), c.publicCase(), c.points(), verdict, millis, c.input(),
                    c.expected(), actual, error));
        }
        run.finish(outcome.compiled(), cut(outcome.compileMessage(), STORED_ERROR), results);
    }

    private void fail(Long runId, String message) {
        try {
            tx.executeWithoutResult(s -> runs.findById(runId).ifPresent(r -> r.markFailed(GradingStatus.ERROR, message)));
        } catch (RuntimeException e) {
            log.warn("채점 오류 상태를 기록하지 못했습니다", e);
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) + "\n... (생략)" : s;
    }

    // ---------- 조회 ----------

    /** 이 과제가 자동 채점 대상인지 (설정과 케이스가 있는지) */
    @Transactional(readOnly = true)
    public boolean configured(Long assignmentId) {
        return configs.findByAssignmentId(assignmentId).isPresent() && cases.countByAssignmentId(assignmentId) > 0;
    }

    /** 제출 한 건의 채점 결과. 자동 채점 과제가 아니면 null. 비공개 케이스의 입출력은 학생에게 숨긴다 */
    @Transactional(readOnly = true)
    public GradingView forSubmission(Long submissionId, AppUser viewer) {
        Submission sub = access.submission(submissionId, viewer);
        if (!configured(sub.getAssignment().getId())) {
            return null;
        }
        boolean up = runner.available();
        GradingRun run = runs.findBySubmissionId(submissionId).orElse(null);
        if (run == null) {
            return new GradingView("NONE", false, null, 0, 0, -1, 0, 0, null, null, List.of(), up);
        }
        boolean showAll = viewer.isProfessor();
        List<CaseView> list = run.getResults().stream().map(r -> {
            boolean detail = showAll || r.isPublicCase();
            return new CaseView(r.getCaseName(), r.isPublicCase(), r.getVerdict().name(), r.getPoints(), r.getAwarded(),
                    r.getDurationMs(), detail ? r.getInput() : null, detail ? r.getExpected() : null,
                    detail ? r.getActual() : null, detail ? r.getError() : null);
        }).toList();
        return new GradingView(run.getStatus().name(), Boolean.TRUE.equals(run.getCompileOk()), run.getCompileMessage(),
                run.getPassedCount(), run.getTotalCount(), run.percent(), run.getScore(), run.getTotalPoints(),
                run.getMessage(), run.getRunner(), list, up);
    }

    /** 한 학생의 버전별 통과율 추이. 자동 채점 과제가 아니면 null (본인 또는 교수만) */
    @Transactional(readOnly = true)
    public TrendView trend(Long assignmentId, Long studentId, AppUser viewer) {
        access.studentHistory(assignmentId, studentId, viewer);
        if (!configured(assignmentId)) {
            return null;
        }
        List<Submission> subs = submissions.findByAssignmentIdAndStudentIdOrderByVersionNoAsc(assignmentId, studentId);
        Map<Long, GradingRun> byId = new HashMap<>();
        if (!subs.isEmpty()) {
            runs.findBySubmissionIdIn(subs.stream().map(Submission::getId).toList())
                    .forEach(r -> byId.put(r.getSubmission().getId(), r));
        }
        List<TrendPoint> points = new ArrayList<>();
        for (Submission s : subs) {
            GradingRun r = byId.get(s.getId());
            points.add(r == null
                    ? new TrendPoint(s.getVersionNo(), s.getId(), "NONE", false, 0, 0, -1)
                    : new TrendPoint(s.getVersionNo(), s.getId(), r.getStatus().name(),
                    Boolean.TRUE.equals(r.getCompileOk()), r.getPassedCount(), r.getTotalCount(), r.percent()));
        }
        List<TrendPoint> graded = points.stream().filter(TrendPoint::graded).toList();
        int delta = graded.size() < 2 ? 0 : graded.get(graded.size() - 1).percent() - graded.get(0).percent();
        return new TrendView(points, delta, graded.size());
    }

    /** 제출 이력 표에 붙일 한 줄 요약 (제출 ID → 요약). 자동 채점 과제가 아니면 빈 맵 */
    @Transactional(readOnly = true)
    public Map<Long, AutoGrade> autoGrades(Long assignmentId, List<Long> submissionIds) {
        Map<Long, AutoGrade> map = new HashMap<>();
        if (submissionIds.isEmpty() || !configured(assignmentId)) {
            return map;
        }
        Map<Long, GradingRun> byId = new HashMap<>();
        runs.findBySubmissionIdIn(submissionIds).forEach(r -> byId.put(r.getSubmission().getId(), r));
        for (Long id : submissionIds) {
            map.put(id, summarize(byId.get(id)));
        }
        return map;
    }

    static AutoGrade summarize(GradingRun r) {
        if (r == null) {
            return new AutoGrade("-", -1, "NONE");
        }
        return switch (r.getStatus()) {
            case QUEUED, RUNNING -> new AutoGrade("채점 중", -1, r.getStatus().name());
            case UNAVAILABLE -> new AutoGrade("채점 불가", -1, "UNAVAILABLE");
            case ERROR -> new AutoGrade("채점 오류", -1, "ERROR");
            case DONE -> Boolean.FALSE.equals(r.getCompileOk())
                    ? new AutoGrade("컴파일 오류", 0, "DONE")
                    : new AutoGrade(r.getPassedCount() + "/" + r.getTotalCount() + " (" + r.percent() + "%)", r.percent(), "DONE");
        };
    }

    /** 통계용: 학생 ID → 버전 순서대로 통과율(%) 목록 (채점되지 않은 버전은 -1) */
    @Transactional(readOnly = true)
    public Map<Long, List<Integer>> passRatesByStudent(Long assignmentId) {
        Map<Long, List<Integer>> map = new LinkedHashMap<>();
        if (!configured(assignmentId)) {
            return map;
        }
        Map<Long, GradingRun> byId = new HashMap<>();
        runs.findBySubmissionAssignmentId(assignmentId).forEach(r -> byId.put(r.getSubmission().getId(), r));
        List<Submission> all = new ArrayList<>(submissions.findByAssignmentIdOrderBySubmittedAtAsc(assignmentId));
        all.sort(java.util.Comparator.comparingInt(Submission::getVersionNo));
        for (Submission s : all) {
            GradingRun r = byId.get(s.getId());
            int percent = r != null && r.getStatus() == GradingStatus.DONE ? r.percent() : -1;
            map.computeIfAbsent(s.getStudent().getId(), k -> new ArrayList<>()).add(percent);
        }
        return map;
    }
}
