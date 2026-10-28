package com.submithub.storage;

import com.submithub.AppProperties;
import com.submithub.service.NotFoundException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 업로드 파일을 서버 디스크에 저장한다.
 * 저장 이름은 항상 UUID 이고 사용자가 낸 파일명/경로는 디스크 경로에 쓰지 않는다 (경로 조작 방지).
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(AppProperties props) {
        this.root = Path.of(props.storageDir()).toAbsolutePath().normalize();
    }

    /** @return 저장 이름(UUID) */
    public String store(byte[] bytes) {
        String name = UUID.randomUUID().toString();
        try {
            Files.createDirectories(root);
            Files.write(resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패", e);
        }
        return name;
    }

    public byte[] read(String name) {
        try {
            return Files.readAllBytes(resolve(name));
        } catch (IOException e) {
            throw new NotFoundException("저장된 파일을 찾을 수 없습니다.");
        }
    }

    /** 트랜잭션이 롤백됐을 때 이미 써 둔 파일을 정리한다. 실패해도 무시한다. */
    public void deleteQuietly(String name) {
        try {
            Files.deleteIfExists(resolve(name));
        } catch (IOException | RuntimeException ignored) {
            // 정리 실패는 치명적이지 않다
        }
    }

    /** 시연 데이터 초기화 시 업로드된 모든 파일을 삭제한다. */
    public void clearAll() {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(root)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private Path resolve(String name) {
        try {
            UUID.fromString(name);   // UUID 형식만 허용
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 저장 이름");
        }
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("저장 폴더 밖의 경로");
        }
        return p;
    }
}
