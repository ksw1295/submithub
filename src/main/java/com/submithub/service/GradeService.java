package com.submithub.service;

import com.submithub.domain.Grade;
import com.submithub.domain.GradeRepository;
import com.submithub.domain.Submission;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 버전 단위 점수와 총평 */
@Service
public class GradeService {

    public record GradeView(int score, String summary, LocalDateTime createdAt, String professorName) {
    }

    private final GradeRepository grades;
    private final UserRepository users;
    private final AccessService access;

    public GradeService(GradeRepository grades, UserRepository users, AccessService access) {
        this.grades = grades;
        this.users = users;
        this.access = access;
    }

    @Transactional
    public void save(Long submissionId, Integer score, String summary, AppUser professor) {
        if (!professor.isProfessor()) {
            throw new ForbiddenException("교수만 점수를 입력할 수 있습니다.");
        }
        Submission s = access.submission(submissionId, professor);
        if (score == null || score < 0 || score > 100) {
            throw new BusinessException("점수는 0~100 사이의 숫자로 입력하세요.");
        }
        String text = summary == null ? null : summary.strip();
        if (text != null && text.length() > 2000) {
            throw new BusinessException("총평은 2000자 이하로 입력하세요.");
        }
        var prof = users.getReferenceById(professor.getId());
        grades.findBySubmissionId(s.getId()).ifPresentOrElse(
                g -> g.update(prof, score, text),
                () -> grades.save(new Grade(s, prof, score, text)));
    }

    /** 권한 검사가 끝난 제출물에 대해서만 호출한다 */
    @Transactional(readOnly = true)
    public GradeView get(Long submissionId) {
        return grades.findBySubmissionId(submissionId)
                .map(g -> new GradeView(g.getScore(), g.getSummary(), g.getCreatedAt(), g.getProfessor().getName()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Integer score(Long submissionId) {
        return grades.findBySubmissionId(submissionId).map(Grade::getScore).orElse(null);
    }
}
