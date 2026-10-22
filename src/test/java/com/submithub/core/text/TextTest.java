package com.submithub.core.text;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    private static final String KOREAN = "// 합계를 구한다\nint 합계 = 0;";

    @Test
    void decodesUtf8() {
        TextDecoder.Decoded d = TextDecoder.decode(KOREAN.getBytes(StandardCharsets.UTF_8));
        assertEquals(KOREAN, d.text());
        assertEquals(StandardCharsets.UTF_8, d.charset());
    }

    @Test
    void decodesMs949() {
        TextDecoder.Decoded d = TextDecoder.decode(KOREAN.getBytes(Charset.forName("MS949")));
        assertEquals(KOREAN, d.text());
        assertEquals("x-windows-949", d.charset().name());
    }

    @Test
    void stripsUtf8Bom() {
        byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        assertEquals("abc", TextDecoder.decode(withBom).text());
    }

    @Test
    void detectsBinary() {
        assertTrue(TextDecoder.looksBinary(new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00}));
        assertFalse(TextDecoder.looksBinary("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void splitsAllLineEndings() {
        assertEquals(List.of("a", "b", "c", "d"), LineSplitter.split("a\r\nb\rc\nd"));
        assertEquals(List.of("a", "b"), LineSplitter.split("a\nb\n"));
        assertEquals(List.of("a", "", "b"), LineSplitter.split("a\n\nb"));
        assertEquals(List.of(), LineSplitter.split(""));
    }
}
