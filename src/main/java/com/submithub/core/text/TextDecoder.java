package com.submithub.core.text;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 업로드된 파일 바이트를 문자열로 바꾼다.
 * 학생들이 Windows 메모장/구버전 IDE에서 저장한 CP949(MS949) 파일도 깨지지 않도록
 * UTF-8을 엄격하게 먼저 시도하고, 실패하면 MS949로 읽는다.
 */
public final class TextDecoder {

    private static final Charset MS949 = Charset.forName("MS949");

    private TextDecoder() {
    }

    public record Decoded(String text, Charset charset) {
    }

    public static Decoded decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new Decoded("", StandardCharsets.UTF_8);
        }
        int offset = hasUtf8Bom(bytes) ? 3 : 0;
        try {
            String text = strict(StandardCharsets.UTF_8)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
            return new Decoded(text, StandardCharsets.UTF_8);
        } catch (CharacterCodingException notUtf8) {
            try {
                return new Decoded(strict(MS949).decode(ByteBuffer.wrap(bytes)).toString(), MS949);
            } catch (CharacterCodingException notMs949) {
                // 둘 다 아니면 깨진 글자를 대체 문자로 바꿔서라도 보여준다
                return new Decoded(new String(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
        }
    }

    /** 텍스트로 보기 어려운 바이너리인지 (NUL 바이트 포함 여부로 판단) */
    public static boolean looksBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8000);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUtf8Bom(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF;
    }

    private static CharsetDecoder strict(Charset cs) {
        return cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }
}
