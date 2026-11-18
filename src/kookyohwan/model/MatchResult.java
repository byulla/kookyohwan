package kookyohwan.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchResult {
    private final SwapRequest request;
    private final Course receiveCourse;
    private final MatchVerdict verdict;
    private final String reason;
    private final List<Course> droppable;

    private MatchResult(SwapRequest request, Course receiveCourse, MatchVerdict verdict, String reason) {
        this(request, receiveCourse, verdict, reason, Collections.emptyList());
    }

    private MatchResult(SwapRequest request, Course receiveCourse, MatchVerdict verdict, String reason,
                        List<Course> droppable) {
        this.request = request;
        this.receiveCourse = receiveCourse;
        this.verdict = verdict;
        this.reason = reason;
        this.droppable = Collections.unmodifiableList(new ArrayList<>(droppable));
    }

    public static MatchResult resolvable(SwapRequest request, Course receiveCourse, String reason,
                                         List<Course> droppable) {
        return new MatchResult(request, receiveCourse, MatchVerdict.EXCLUDED_MINE, reason, droppable);
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

    public boolean isResolvable() {
        return !droppable.isEmpty();
    }

    public List<Course> getDroppable() {
        return droppable;
    }

    public boolean isPossible() {
        return verdict == MatchVerdict.POSSIBLE;
    }
}
