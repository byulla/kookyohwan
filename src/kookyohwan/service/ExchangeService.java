package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.MatchResult;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ExchangeService {
    private final RequestBoard board;
    private final StudentRegistry registry;
    private final MatchService matcher;

    public ExchangeService(RequestBoard board, StudentRegistry registry, MatchService matcher) {
        this.board = board;
        this.registry = registry;
        this.matcher = matcher;
    }

    public List<SwapRequest> confirm(SwapRequest mine, SwapRequest other) {
        return confirm(mine, other, false);
    }

    public List<SwapRequest> confirm(SwapRequest mine, SwapRequest other, boolean dropConflicts) {
        Student me = registry.find(mine.getStudentId()).orElseThrow(
                () -> new RequestRejectedException("요청 #" + mine.getId(), "요청자의 시간표를 찾을 수 없습니다"));
        if (!mine.isWaiting() || !other.isWaiting()) {
            throw new RequestRejectedException("요청 #" + mine.getId() + " <-> #" + other.getId(),
                    "대기 상태가 아닌 요청은 교환할 수 없습니다");
        }
        MatchResult verdict = null;
        for (MatchResult r : matcher.match(mine, me.getTimetable())) {
            if (r.getRequest().getId() == other.getId()) {
                verdict = r;
            }
        }
        if (verdict == null) {
            throw new RequestRejectedException("요청 #" + other.getId(), "내 요청과 조건이 맞지 않습니다");
        }
        if (!verdict.isPossible() && !(dropConflicts && verdict.isResolvable())) {
            throw new RequestRejectedException("요청 #" + other.getId(), verdict.getReason());
        }

        Course give = mine.getGiveCourse();
        Course receive = other.getGiveCourse();

        List<Course> leaving = new ArrayList<>(verdict.getDroppable());
        leaving.add(give);
        if (!me.getTimetable().replace(leaving, receive)) {
            throw new RequestRejectedException("요청 #" + mine.getId(), "내 시간표를 바꿀 수 없습니다");
        }
        Optional<Student> partner = registry.find(other.getStudentId());
        if (partner.isPresent()
                && !partner.get().getTimetable().replace(Collections.singletonList(receive), give)) {
            me.getTimetable().remove(receive);
            for (Course c : leaving) {
                me.getTimetable().restore(c);
            }
            throw new RequestRejectedException("요청 #" + other.getId(), "상대 시간표를 바꿀 수 없습니다");
        }
        board.complete(mine.getId());
        board.complete(other.getId());

        List<SwapRequest> cancelled = new ArrayList<>();
        cancelled.addAll(cancelStale(mine.getStudentId(), give, receive));
        cancelled.addAll(cancelStale(other.getStudentId(), receive, give));
        return cancelled;
    }

    private List<SwapRequest> cancelStale(String studentId, Course gone, Course gained) {
        List<SwapRequest> cancelled = new ArrayList<>();
        for (SwapRequest r : board.waitingOf(studentId)) {
            boolean stale = r.getGiveCourse().getCode().equals(gone.getCode())
                    || r.getWantName().equals(gained.getName());
            if (stale && board.cancel(r.getId())) {
                cancelled.add(r);
            }
        }
        return cancelled;
    }
}
