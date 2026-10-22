package com.submithub.storage;

import com.submithub.AppProperties;
import com.submithub.service.BusinessException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 업로드된 파일(들)을 제출할 파일 목록으로 바꾼다.
 * - zip 은 메모리에서 풀고, 내부 경로를 정규화한다. 절대 경로·".." 가 있으면 거부한다 (Zip Slip 방지).
 * - 파일 크기, 총 크기, 파일 수 제한 (zip 폭탄 방지: 풀면서 크기를 센다).
 */
@Component
public class UploadProcessor {

    public record Upload(String filename, byte[] bytes) {
    }

    public record Processed(String path, byte[] bytes) {
    }

    private final AppProperties props;

    public UploadProcessor(AppProperties props) {
        this.props = props;
    }

    public List<Processed> process(List<Upload> uploads) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        long[] total = {0};
        for (Upload u : uploads) {
            String name = baseName(u.filename());
            if (name.isEmpty()) {
                continue;
            }
            if (name.toLowerCase().endsWith(".zip")) {
                extractZip(u.bytes(), out, total);
            } else {
                add(out, name, u.bytes(), total);
            }
        }
        if (out.isEmpty()) {
            throw new BusinessException("제출할 파일을 선택하세요.");
        }
        List<Processed> result = new ArrayList<>();
        out.forEach((path, bytes) -> result.add(new Processed(path, bytes)));
        return result;
    }

    /** 업로드 파일명에서 경로를 떼고 이름만 남긴다 */
    static String baseName(String filename) {
        if (filename == null) {
            return "";
        }
        String n = filename.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    /** zip 안의 경로를 검사하고 정규화한다. 무시할 파일이면 null. 위험한 경로면 예외 */
    static String normalizeZipPath(String entryName) {
        String n = entryName.replace('\\', '/');
        if (n.indexOf('\0') >= 0 || n.startsWith("/") || n.matches("^[A-Za-z]:.*")) {
            throw new BusinessException("zip 안에 허용되지 않는 경로가 있습니다: " + printable(entryName));
        }
        List<String> segments = new ArrayList<>();
        for (String seg : n.split("/")) {
            if (seg.equals("..")) {
                throw new BusinessException("zip 안에 허용되지 않는 경로가 있습니다: " + printable(entryName));
            }
            if (!seg.isEmpty() && !seg.equals(".")) {
                segments.add(seg);
            }
        }
        if (segments.isEmpty()) {
            return null;
        }
        String last = segments.get(segments.size() - 1);
        if (segments.get(0).equals("__MACOSX") || last.equals(".DS_Store") || last.equalsIgnoreCase("Thumbs.db")) {
            return null;
        }
        String joined = String.join("/", segments);
        if (joined.length() > 255) {
            throw new BusinessException("zip 안의 경로가 너무 깁니다.");
        }
        return joined;
    }

    private static String printable(String s) {
        String t = s.replaceAll("[\\p{Cntrl}]", "?");
        return t.length() > 80 ? t.substring(0, 80) + "..." : t;
    }

    private void extractZip(byte[] zipBytes, Map<String, byte[]> out, long[] total) {
        // 윈도우에서 만든 zip 은 한글 파일명이 CP949 인 경우가 있어, UTF-8 이 실패하면 MS949 로 다시 읽는다
        Map<String, byte[]> tmp = new LinkedHashMap<>();
        long[] tmpTotal = {total[0]};
        try {
            readZip(zipBytes, StandardCharsets.UTF_8, out, tmp, tmpTotal);
        } catch (IllegalArgumentException | IOException firstFailure) {
            tmp.clear();
            tmpTotal[0] = total[0];
            try {
                readZip(zipBytes, Charset.forName("MS949"), out, tmp, tmpTotal);
            } catch (IllegalArgumentException | IOException e) {
                throw new BusinessException("zip 파일을 읽을 수 없습니다. 올바른 zip 인지 확인하세요.");
            }
        }
        out.putAll(tmp);
        total[0] = tmpTotal[0];
    }

    private void readZip(byte[] zipBytes, Charset charset, Map<String, byte[]> existing,
                         Map<String, byte[]> tmp, long[] total) throws IOException {
        int entries = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes), charset)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                if (++entries > props.maxFiles() * 5) {
                    throw new BusinessException("zip 안의 항목이 너무 많습니다.");
                }
                String path = normalizeZipPath(e.getName());
                if (path == null) {
                    continue;
                }
                byte[] data = readLimited(zin, path);
                if (existing.containsKey(path)) {
                    throw new BusinessException("같은 경로의 파일이 여러 번 있습니다: " + printable(path));
                }
                add(tmp, path, data, total);
            }
        }
    }

    private byte[] readLimited(InputStream in, String path) throws IOException {
        byte[] buf = in.readNBytes((int) Math.min(props.maxFileBytes() + 1, Integer.MAX_VALUE - 8));
        if (buf.length > props.maxFileBytes()) {
            throw new BusinessException("파일이 너무 큽니다 (최대 " + (props.maxFileBytes() / 1024 / 1024)
                    + "MB): " + printable(path));
        }
        return buf;
    }

    private void add(Map<String, byte[]> out, String path, byte[] bytes, long[] total) {
        if (bytes.length > props.maxFileBytes()) {
            throw new BusinessException("파일이 너무 큽니다 (최대 " + (props.maxFileBytes() / 1024 / 1024)
                    + "MB): " + printable(path));
        }
        total[0] += bytes.length;
        if (total[0] > props.maxTotalBytes()) {
            throw new BusinessException("제출 크기가 너무 큽니다 (최대 " + (props.maxTotalBytes() / 1024 / 1024) + "MB).");
        }
        if (out.containsKey(path)) {
            throw new BusinessException("같은 이름의 파일이 여러 개 있습니다: " + printable(path));
        }
        if (out.size() >= props.maxFiles()) {
            throw new BusinessException("파일이 너무 많습니다 (최대 " + props.maxFiles() + "개).");
        }
        out.put(path, bytes);
    }
}
