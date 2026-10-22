package com.submithub.domain;

/** 자동 채점을 지원하는 언어 */
public enum GradingLanguage {
    JAVA("Java", ".java"), PYTHON("Python", ".py");

    private final String label;
    private final String extension;

    GradingLanguage(String label, String extension) {
        this.label = label;
        this.extension = extension;
    }

    public String getLabel() {
        return label;
    }

    public String getExtension() {
        return extension;
    }
}
