package com.submithub.web;

import com.submithub.service.BusinessException;
import com.submithub.service.ForbiddenException;
import com.submithub.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 공통 오류 처리: 예외가 나도 앱이 조용히 멈추지 않고 오류 페이지나 안내 메시지를 보여준다. */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException e, HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", e.getMessage());
        return "redirect:" + backUrl(request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "업로드 크기 제한을 넘었습니다.");
        return "redirect:" + backUrl(request);
    }

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException e, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("status", 404);
        model.addAttribute("detail", e.getMessage());
        return "error/4xx";
    }

    @ExceptionHandler(ForbiddenException.class)
    public String forbidden(ForbiddenException e, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("status", 403);
        model.addAttribute("detail", e.getMessage());
        return "error/4xx";
    }

    /** Spring Security 가 처리하도록 다시 던진다 (로그인 페이지 이동 / 403) */
    @ExceptionHandler(AccessDeniedException.class)
    public void accessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception e, Model model, jakarta.servlet.http.HttpServletResponse response)
            throws Exception {
        // 404/405/400 처럼 프레임워크가 알맞은 상태 코드로 처리하는 예외는 그대로 넘긴다
        if (e instanceof org.springframework.web.ErrorResponse || e instanceof org.springframework.beans.TypeMismatchException) {
            throw e;
        }
        log.error("처리되지 않은 오류", e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("status", 500);
        model.addAttribute("detail", "잠시 후 다시 시도해 주세요.");
        return "error/5xx";
    }

    /** 오류가 난 폼이 있던 화면으로 돌려보낸다. 같은 사이트의 경로만 허용한다. */
    private static String backUrl(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                java.net.URI uri = java.net.URI.create(referer);
                String host = request.getServerName();
                if (host.equals(uri.getHost()) && uri.getRawPath() != null && uri.getRawPath().startsWith("/")) {
                    return uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                }
            } catch (IllegalArgumentException ignored) {
                // 잘못된 Referer 는 무시
            }
        }
        return "/";
    }
}
