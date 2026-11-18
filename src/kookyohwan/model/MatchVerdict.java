package kookyohwan.model;

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
