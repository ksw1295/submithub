package com.submithub.github;

import com.submithub.service.BusinessException;

/** GitHub 호출이 실패했을 때. 메시지는 학생에게 그대로 보여줘도 되는 안내 문구다. */
public class GitHubException extends BusinessException {

    public GitHubException(String message) {
        super(message);
    }
}
