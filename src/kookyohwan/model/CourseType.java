package kookyohwan.model;

public enum CourseType {
    MAJOR("전공"), GENERAL("교양"), TEACHING("교직"), MILITARY("군사");

    private final String label;

    CourseType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
