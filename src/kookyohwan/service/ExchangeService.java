package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.MatchResult;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 교환 확정: 사용자가 '가능'으로 나온 상대를 직접 골라 확정했을 때만 두 사람의 시간표와 요청 상태를 바꾼다.
 * 매칭 실행(MatchService)은 여전히 조회 전용이다 — 자동으로 바꾸지 않는다는 결정(16절 Q5)은 그대로 두고,
 * 합의가 끝난 뒤 결과를 프로그램에 반영하는 동작을 따로 둔 것.
 * 여기서도 직접 바꾸지 않고 시간표는 Timetable.replace, 요청 상태는 RequestBoard에 맡긴다.
 */
public class ExchangeService {
    private final RequestBoard board;
    private final StudentRegistry registry;
    private final MatchService matcher;

    public ExchangeService(RequestBoard board, StudentRegistry registry, MatchService matcher) {
        this.board = board;
        this.registry = registry;
        this.matcher = matcher;
    }

    /**
     * mine(내 요청)과 other(상대 요청)의 교환을 확정한다.
     * 확정 직전에 매칭을 다시 돌려 지금도 '가능'인지 확인하고(그 사이 시간표가 바뀌었을 수 있다),
     * 아니면 RequestRejectedException을 던지고 아무것도 바꾸지 않는다.
     * 돌려주는 값: 내놓는 과목이 사라져서 같이 취소된 다른 대기 요청들.
     */
    public List<SwapRequest> confirm(SwapRequest mine, SwapRequest other) {
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
        if (!verdict.isPossible()) {
            throw new RequestRejectedException("요청 #" + other.getId(), verdict.getReason());
        }

        Course give = mine.getGiveCourse();
        Course receive = other.getGiveCourse();
        // 내 시간표: 내놓는 과목을 빼고 받을 과목을 넣는다. 상대 시간표는 그 반대 (상대 시간표가 없으면 내 쪽만)
        me.getTimetable().replace(Collections.singletonList(give), receive);
        Optional<Student> partner = registry.find(other.getStudentId());
        if (partner.isPresent()) {
            partner.get().getTimetable().replace(Collections.singletonList(receive), give);
        }
        board.complete(mine.getId());
        board.complete(other.getId());

        // 같은 과목을 내놓던 다른 대기 요청은 과목이 없어졌으므로 취소한다 (요청만 남는 상태를 막는다)
        List<SwapRequest> cancelled = new ArrayList<>();
        cancelled.addAll(cancelGiving(mine.getStudentId(), give));
        cancelled.addAll(cancelGiving(other.getStudentId(), receive));
        return cancelled;
    }

    private List<SwapRequest> cancelGiving(String studentId, Course gone) {
        List<SwapRequest> cancelled = new ArrayList<>();
        for (SwapRequest r : board.waitingOf(studentId)) {
            if (r.getGiveCourse().getCode().equals(gone.getCode()) && board.cancel(r.getId())) {
                cancelled.add(r);
            }
        }
        return cancelled;
    }
}
