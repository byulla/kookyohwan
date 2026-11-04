package kookyohwan.model;

/** 과목 구분. 교환 요청은 GENERAL(교양)만 허용한다(계획서 5.3). */
public enum CourseType {
    MAJOR("전공"), GENERAL("교양");

    private final String label;

    CourseType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
