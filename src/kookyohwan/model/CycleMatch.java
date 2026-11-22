package kookyohwan.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CycleMatch {
    private final List<SwapRequest> requests;
    private final MatchVerdict verdict;
    private final String reason;

    private CycleMatch(List<SwapRequest> requests, MatchVerdict verdict, String reason) {
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
        this.verdict = verdict;
        this.reason = reason;
    }

    public static CycleMatch possible(List<SwapRequest> requests, String reason) {
        return new CycleMatch(requests, MatchVerdict.POSSIBLE, reason);
    }

    public static CycleMatch excluded(List<SwapRequest> requests, MatchVerdict verdict, String reason) {
        if (verdict == MatchVerdict.POSSIBLE) {
            throw new IllegalArgumentException("excluded()에는 제외 판정만 넣을 수 있습니다.");
        }
        return new CycleMatch(requests, verdict, reason);
    }

    public List<SwapRequest> getRequests() {
        return requests;
    }

    public Course receiveCourseOf(int index) {
        return requests.get((index + 1) % requests.size()).getGiveCourse();
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
