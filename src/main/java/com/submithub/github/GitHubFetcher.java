package com.submithub.github;

import com.submithub.AppProperties;
import com.submithub.storage.UploadProcessor;
import com.submithub.storage.UploadProcessor.Processed;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GitHub 커밋의 파일을 제출할 파일 목록으로 바꾼다. 소스 파일로 볼 확장자만 받고(그림·실행 파일 등은 제외),
 * 빌드 산출물·의존성 폴더는 건너뛴다. 크기·파일 수 제한은 일반 업로드와 같다.
 */
@Component
public class GitHubFetcher {

    static final Set<String> EXTENSIONS = Set.of(
            "java", "kt", "kts", "scala", "py", "ipynb", "c", "h", "cc", "cpp", "hpp", "cs", "js", "jsx", "ts", "tsx",
            "go", "rs", "rb", "php", "swift", "dart", "r", "sql", "sh", "html", "css", "xml", "json", "yml", "yaml",
            "toml", "gradle", "properties", "md", "txt", "csv");
    static final Set<String> NAMES = Set.of("makefile", "dockerfile", "readme", "license");
    static final Set<String> SKIPPED_DIRS = Set.of(".git", ".github", ".idea", ".vscode", "node_modules", "target",
            "build", "out", "dist", "__pycache__", ".gradle", "venv", ".venv", "bin", "obj");

    private final GitHubClient client;
    private final UploadProcessor processor;
    private final AppProperties props;

    public GitHubFetcher(GitHubClient client, UploadProcessor processor, AppProperties props) {
        this.client = client;
        this.processor = processor;
        this.props = props;
    }

    public GitHubClient.Commit resolve(GitHubRepoRef ref) {
        return client.resolve(ref.owner(), ref.repo(), ref.ref());
    }

    public List<Processed> files(GitHubRepoRef ref, String sha) {
        // 압축된 다운로드는 풀었을 때의 제한보다 넉넉히 받되 상한을 둔다 (그림 등 걸러질 파일이 섞여 있을 수 있다)
        byte[] zip = client.zipball(ref.owner(), ref.repo(), sha, props.maxTotalBytes() * 2);
        return processor.processRepositoryZip(zip, GitHubFetcher::acceptable);
    }

    static boolean acceptable(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (SKIPPED_DIRS.contains(parts[i].toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        String name = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return NAMES.contains(name);
        }
        return EXTENSIONS.contains(name.substring(dot + 1));
    }
}
