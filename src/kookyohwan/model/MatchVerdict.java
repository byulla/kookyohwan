package kookyohwan.model;

/** 매칭 판정. 제외 사유가 내 쪽인지 상대 쪽인지 구분하되 화면 라벨은 둘 다 "제외". */
public enum MatchVerdict {
    POSSIBLE("가능"), EXCLUDED_MINE("제외"), EXCLUDED_PARTNER("제외");

    private final String label;

    MatchVerdict(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
