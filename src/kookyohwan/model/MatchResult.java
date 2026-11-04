package kookyohwan.model;

/** 매칭 결과 한 줄: 후보 요청, 내가 받을 과목(상대 분반), 판정, 사유. */
public class MatchResult {
    private final SwapRequest request;
    private final Course receiveCourse;
    private final MatchVerdict verdict;
    private final String reason;

    private MatchResult(SwapRequest request, Course receiveCourse, MatchVerdict verdict, String reason) {
        this.request = request;
        this.receiveCourse = receiveCourse;
        this.verdict = verdict;
        this.reason = reason;
    }

    public static MatchResult possible(SwapRequest request, Course receiveCourse) {
        return new MatchResult(request, receiveCourse, MatchVerdict.POSSIBLE, "겹침 없음");
    }

    public static MatchResult possible(SwapRequest request, Course receiveCourse, String reason) {
        return new MatchResult(request, receiveCourse, MatchVerdict.POSSIBLE, reason);
    }

    public static MatchResult excluded(SwapRequest request, Course receiveCourse,
                                       MatchVerdict verdict, String reason) {
        if (verdict == MatchVerdict.POSSIBLE) {
            throw new IllegalArgumentException("excluded()에는 제외 판정만 넣을 수 있습니다.");
        }
        return new MatchResult(request, receiveCourse, verdict, reason);
    }

    public SwapRequest getRequest() {
        return request;
    }

    public Course getReceiveCourse() {
        return receiveCourse;
    }

    public MatchVerdict getVerdict() {
        return verdict;
    }

    public String getReason() {
        return reason;
    }

    public boolean isPossible() {
        return verdict == MatchVerdict.POSSIBLE;
    }
}
