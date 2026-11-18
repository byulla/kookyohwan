package kookyohwan.model;

public enum Day {
    MON("월"), TUE("화"), WED("수"), THU("목"), FRI("금"), SAT("토"), SUN("일");

    private final String label;

    Day(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Day fromLabel(String label) {
        for (Day d : values()) {
            if (d.label.equals(label)) {
                return d;
            }
        }
        throw new IllegalArgumentException("알 수 없는 요일: " + label);
    }
}
