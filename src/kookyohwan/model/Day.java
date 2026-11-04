package kookyohwan.model;

/** 요일. 문자열 대신 enum을 써서 "월"/"월요일"/"Mon"이 섞이는 일을 막는다. */
public enum Day {
    MON("월"), TUE("화"), WED("수"), THU("목"), FRI("금");

    private final String label;

    Day(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** "월" -> MON. 파일 파싱과 화면 출력이 같은 라벨을 쓴다. */
    public static Day fromLabel(String label) {
        for (Day d : values()) {
            if (d.label.equals(label)) {
                return d;
            }
        }
        throw new IllegalArgumentException("알 수 없는 요일: " + label);
    }
}
