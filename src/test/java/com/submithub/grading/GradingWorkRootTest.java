package com.submithub.grading;

import com.submithub.AppProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradingWorkRootTest {

    private static AppProperties props() {
        return new AppProperties("./uploads", false, "", 0, 0, 0, null, null, null, null);
    }

    @Test
    void 설정이_없으면_업로드_폴더_아래_grading_work() {
        Path expected = Path.of("./uploads").toAbsolutePath().normalize().resolve("grading-work");
        assertThat(DockerGradingRunner.resolveWorkRoot(props(), "")).isEqualTo(expected);
        assertThat(DockerGradingRunner.resolveWorkRoot(props(), null)).isEqualTo(expected);
    }

    @Test
    void 설정한_절대_경로를_그대로_쓰고_상대_경로는_거부한다() {
        Path abs = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("gw");
        assertThat(DockerGradingRunner.resolveWorkRoot(props(), abs.toString())).isEqualTo(abs.normalize());
        assertThatThrownBy(() -> DockerGradingRunner.resolveWorkRoot(props(), "relative/dir"))
                .isInstanceOf(IllegalStateException.class);
    }
}
