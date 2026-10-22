package com.submithub.service;

import com.submithub.core.text.LineSplitter;
import com.submithub.domain.ReviewComment;
import com.submithub.domain.ReviewCommentRepository;
import com.submithub.domain.SubmissionFile;
import com.submithub.domain.UserRepository;
import com.submithub.security.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 줄 단위 코드 리뷰: 교수가 줄(범위)에 코멘트를 남기고, 교수와 학생이 답글을 단다. */
@Service
public class ReviewService {

    static final int MAX_BODY = 2000;

    public record CommentView(Long id, String authorName, boolean professor, String body, LocalDateTime createdAt) {
    }

    public record CommentThread(Long id, int startLine, int endLine, CommentView root, boolean resolved,
                                List<CommentView> replies) {
    }

    private final ReviewCommentRepository comments;
    private final UserRepository users;
    private final AccessService access;

    public ReviewService(ReviewCommentRepository comments, UserRepository users, AccessService access) {
        this.comments = comments;
        this.users = users;
        this.access = access;
    }

    /** 교수가 파일의 startLine~endLine 에 코멘트를 남긴다 */
    @Transactional
    public Long addComment(Long fileId, int startLine, int endLine, String body, AppUser user) {
        if (!user.isProfessor()) {
            throw new ForbiddenException("코드 리뷰 코멘트는 교수만 작성할 수 있습니다.");
        }
        SubmissionFile file = access.file(fileId, user);
        if (file.isBinary()) {
            throw new BusinessException("바이너리 파일에는 코멘트를 달 수 없습니다.");
        }
        int lines = LineSplitter.split(file.getContentText()).size();
        if (startLine < 1 || endLine < startLine || endLine > lines) {
            throw new BusinessException("잘못된 줄 범위입니다. (1~" + lines + ")");
        }
        String text = cleanBody(body);
        return comments.save(new ReviewComment(file, users.getReferenceById(user.getId()),
                startLine, endLine, text, null)).getId();
    }

    /** 교수 또는 해당 제출물의 학생이 코멘트에 답글을 단다 */
    @Transactional
    public Long reply(Long commentId, String body, AppUser user) {
        ReviewComment parent = access.comment(commentId, user);
        if (parent.getParent() != null) {
            throw new BusinessException("답글에는 답글을 달 수 없습니다.");
        }
        String text = cleanBody(body);
        return comments.save(new ReviewComment(parent.getSubmissionFile(), users.getReferenceById(user.getId()),
                parent.getStartLine(), parent.getEndLine(), text, parent)).getId();
    }

    /** 학생이 "고쳤어요" 표시를 켜고 끈다 (본인 제출물의 최상위 코멘트만) */
    @Transactional
    public boolean toggleResolved(Long commentId, AppUser user) {
        if (user.isProfessor()) {
            throw new ForbiddenException("학생만 표시할 수 있습니다.");
        }
        ReviewComment c = access.comment(commentId, user);
        if (c.getParent() != null) {
            throw new BusinessException("코멘트에만 표시할 수 있습니다.");
        }
        c.setResolvedByStudent(!c.isResolvedByStudent());
        return c.isResolvedByStudent();
    }

    @Transactional(readOnly = true)
    public List<CommentThread> threads(Long fileId, AppUser viewer) {
        access.file(fileId, viewer);
        return threadsUnchecked(fileId);
    }

    /** 권한 검사가 이미 끝난 호출용 */
    @Transactional(readOnly = true)
    public List<CommentThread> threadsUnchecked(Long fileId) {
        List<ReviewComment> all = comments.findBySubmissionFileIdOrderByCreatedAtAscIdAsc(fileId);
        Map<Long, List<CommentView>> replies = new HashMap<>();
        for (ReviewComment c : all) {
            if (c.getParent() != null) {
                replies.computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>()).add(view(c));
            }
        }
        List<CommentThread> threads = new ArrayList<>();
        for (ReviewComment c : all) {
            if (c.getParent() == null) {
                threads.add(new CommentThread(c.getId(), c.getStartLine(), c.getEndLine(), view(c),
                        c.isResolvedByStudent(), replies.getOrDefault(c.getId(), List.of())));
            }
        }
        threads.sort(java.util.Comparator.comparingInt(CommentThread::endLine).thenComparingInt(CommentThread::startLine));
        return threads;
    }

    /** 화면에서 줄 아래에 코멘트를 붙이기 쉽도록 끝 줄 번호로 묶는다 */
    public static Map<Integer, List<CommentThread>> byEndLine(List<CommentThread> threads) {
        Map<Integer, List<CommentThread>> map = new LinkedHashMap<>();
        for (CommentThread t : threads) {
            map.computeIfAbsent(t.endLine(), k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    private static CommentView view(ReviewComment c) {
        return new CommentView(c.getId(), c.getAuthor().getName(), c.getAuthor().isProfessor(), c.getBody(),
                c.getCreatedAt());
    }

    private static String cleanBody(String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw new BusinessException("내용을 입력하세요.");
        }
        if (text.length() > MAX_BODY) {
            throw new BusinessException("코멘트는 " + MAX_BODY + "자 이하로 입력하세요.");
        }
        return text;
    }
}
