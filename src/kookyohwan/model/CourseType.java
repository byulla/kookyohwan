package kookyohwan.model;

/**
 * 과목 구분. 교환 요청은 GENERAL(교양)만 허용한다(계획서 5.3).
 * 학교 시간표의 이수 구분을 묶은 것: 전필·전선 -> MAJOR, 교필·교선·계교 -> GENERAL, 교직 -> TEACHING, 군사 -> MILITARY.
 */
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
