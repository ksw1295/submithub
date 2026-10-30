package com.submithub.service;

import com.submithub.core.similarity.SimilarityChecker;
import com.submithub.core.text.LineSplitter;
import com.submithub.domain.Assignment;
import com.submithub.domain.SimilarityPair;
import com.submithub.domain.SimilarityPairRepository;
import com.submithub.domain.SimilarityRun;
import com.submithub.domain.SimilarityRunRepository;
import com.submithub.domain.Submission;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.SubmissionRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 같은 과제의 학생별 최신 제출물을 쌍으로 비교해 유사도 순위를 만든다 (Winnowing, MOSS 방식).
 * 교수가 올려둔 뼈대 코드는 비교에서 제외된다.
 */
@Service
public class SimilarityService {

    public static final double DEFAULT_THRESHOLD = 0.70;

    public record PairRow(Long id, Long submissionAId, Long submissionBId, String studentA, String studentB,
                          int versionA, int versionB, double score, double coverageA, double coverageB) {
    }

    public record RunView(Long runId, double threshold, LocalDateTime createdAt, int compared,
                          List<PairRow> pairs, boolean hasBaseline) {
    }

    public record CodeLine(int no, String text, boolean matched) {
    }

    public record FileBlock(String path, List<CodeLine> lines) {
    }

    public record PairView(Long pairId, Long assignmentId, String assignmentTitle, Long courseId,
                           String studentA, String studentB, int versionA, int versionB,
                           double score, double coverageA, double coverageB, int sharedFingerprints,
                           List<FileBlock> filesA, List<FileBlock> filesB, int matchedLinesA, int matchedLinesB) {
    }

    /** 제출물의 텍스트 파일을 이어 붙인 하나의 문서와, 파일별 줄 위치 */
    private record Combined(String text, List<Span> spans) {
    }

    private record Span(String path, int offset, List<String> lines) {
    }

    private final SubmissionRepository submissions;
    private final SimilarityRunRepository runs;
    private final SimilarityPairRepository pairs;
    private final AccessService access;

    public SimilarityService(SubmissionRepository submissions, SimilarityRunRepository runs,
                             SimilarityPairRepository pairs, AccessService access) {
        this.submissions = submissions;
        this.runs = runs;
        this.pairs = pairs;
        this.access = access;
    }

    @Transactional
    public RunView run(Long assignmentId, double threshold, AppUser professor) {
        Assignment a = access.assignmentOfProfessor(assignmentId, professor);
        if (Double.isNaN(threshold) || threshold < 0.05 || threshold > 1.0) {
            throw new BusinessException("기준 유사도는 5%~100% 사이로 입력하세요.");
        }
        Map<String, String> codes = new LinkedHashMap<>();
        Map<String, Submission> byId = new HashMap<>();
        for (Submission s : submissions.findLatestPerStudent(assignmentId)) {
            Combined c = combine(s);
            if (!c.text().isBlank()) {
                codes.put(String.valueOf(s.getId()), c.text());
                byId.put(String.valueOf(s.getId()), s);
            }
        }
        List<SimilarityChecker.Pair> found = SimilarityChecker.compareAll(codes, a.getBaselineCode(), threshold);

        SimilarityRun run = runs.save(new SimilarityRun(a, threshold, codes.size()));
        for (SimilarityChecker.Pair p : found) {
            pairs.save(new SimilarityPair(run, byId.get(p.idA()), byId.get(p.idB()), p.score(),
                    p.coverageA(), p.coverageB()));
        }
        return toView(run, a.getBaselineCode() != null);
    }

    /** 가장 최근 실행 결과. 아직 실행한 적이 없으면 null */
    @Transactional(readOnly = true)
    public RunView latest(Long assignmentId, AppUser professor) {
        Assignment a = access.assignmentOfProfessor(assignmentId, professor);
        return runs.findTopByAssignmentIdOrderByIdDesc(assignmentId)
                .map(r -> toView(r, a.getBaselineCode() != null)).orElse(null);
    }

    @Transactional(readOnly = true)
    public PairView pair(Long pairId, AppUser professor) {
        SimilarityPair p = pairs.findById(pairId).orElseThrow(() -> new NotFoundException("검사 결과를 찾을 수 없습니다."));
        Assignment a = access.assignmentOfProfessor(p.getRun().getAssignment().getId(), professor);

        Combined ca = combine(p.getSubmissionA());
        Combined cb = combine(p.getSubmissionB());
        Set<Long> base = SimilarityChecker.baseline(a.getBaselineCode());
        var docA = SimilarityChecker.document("a", ca.text(), base);
        var docB = SimilarityChecker.document("b", cb.text(), base);
        SimilarityChecker.Pair cmp = SimilarityChecker.compare(docA, docB);

        return new PairView(p.getId(), a.getId(), a.getTitle(), a.getCourse().getId(),
                p.getSubmissionA().getStudent().getName(), p.getSubmissionB().getStudent().getName(),
                p.getSubmissionA().getVersionNo(), p.getSubmissionB().getVersionNo(),
                p.getScore(), p.getCoverageA(), p.getCoverageB(), cmp.sharedFingerprints(),
                blocks(ca, cmp.matchedLinesA()), blocks(cb, cmp.matchedLinesB()),
                cmp.matchedLinesA().size(), cmp.matchedLinesB().size());
    }

    private RunView toView(SimilarityRun run, boolean hasBaseline) {
        List<PairRow> rows = pairs.findByRunIdOrderByScoreDesc(run.getId()).stream()
                .map(p -> new PairRow(p.getId(), p.getSubmissionA().getId(), p.getSubmissionB().getId(),
                        p.getSubmissionA().getStudent().getName(), p.getSubmissionB().getStudent().getName(),
                        p.getSubmissionA().getVersionNo(), p.getSubmissionB().getVersionNo(),
                        p.getScore(), p.getCoverageA(), p.getCoverageB()))
                .toList();
        return new RunView(run.getId(), run.getThreshold(), run.getCreatedAt(), run.getCompared(), rows, hasBaseline);
    }

    /** 텍스트 파일을 경로 순으로 줄 단위로 이어 붙인다 (줄바꿈은 \n 으로 통일) */
    static Combined combine(Submission s) {
        List<SubmissionFile> files = new ArrayList<>(s.getFiles());
        files.sort(Comparator.comparing(SubmissionFile::getPath));
        StringBuilder sb = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        int offset = 0;
        for (SubmissionFile f : files) {
            if (f.isBinary()) {
                continue;
            }
            List<String> lines = LineSplitter.split(f.getContentText());
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            spans.add(new Span(f.getPath(), offset, lines));
            offset += lines.size();
        }
        return new Combined(sb.toString(), spans);
    }

    /** 이어 붙인 문서의 줄 번호(겹치는 줄)를 파일별 화면 블록으로 되돌린다 */
    private static List<FileBlock> blocks(Combined c, Set<Integer> matched) {
        List<FileBlock> blocks = new ArrayList<>();
        for (Span span : c.spans()) {
            List<CodeLine> lines = new ArrayList<>();
            for (int i = 0; i < span.lines().size(); i++) {
                lines.add(new CodeLine(i + 1, span.lines().get(i), matched.contains(span.offset() + i + 1)));
            }
            blocks.add(new FileBlock(span.path(), lines));
        }
        return blocks;
    }
}
