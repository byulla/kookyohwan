package kookyohwan.model;

public enum RequestStatus {
    WAITING("대기"), MATCHED("매칭완료"), CANCELLED("취소");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
