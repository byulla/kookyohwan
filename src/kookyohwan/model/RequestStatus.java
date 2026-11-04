package kookyohwan.model;

/** 교환 요청 상태. 파일에는 enum 이름(WAITING), 화면에는 라벨("대기")을 쓴다. */
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
