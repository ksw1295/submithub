package com.submithub.core.diff;

import java.util.List;

public record DiffResult(List<DiffLine> lines, boolean truncated) {

    public DiffResult(List<DiffLine> lines) {
        this(lines, false);
    }

    public DiffResult {
        lines = List.copyOf(lines);
    }

    public int addedCount() {
        return (int) lines.stream().filter(l -> l.type() == DiffType.ADDED).count();
    }

    public int removedCount() {
        return (int) lines.stream().filter(l -> l.type() == DiffType.REMOVED).count();
    }

    public boolean hasChanges() {
        return lines.stream().anyMatch(l -> l.type() != DiffType.EQUAL);
    }

    /** 예: "+12 / -3" */
    public String summary() {
        return "+" + addedCount() + " / -" + removedCount();
    }
}
