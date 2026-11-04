package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.MatchResult;
import kookyohwan.model.MatchVerdict;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.model.Timetable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R4 + R5 + R5-2 매칭. 조회 전용 — 요청 상태도 시간표도 바꾸지 않는다.
 * 시간 비교 코드는 여기 없다. 겹침 판정은 전부 Timetable.findConflict에 맡긴다.
 */
public class MatchService {
    private final RequestBoard board;
    private final StudentRegistry registry; // 상대 시간표 조회용

    public MatchService(RequestBoard board, StudentRegistry registry) {
        this.board = board;
        this.registry = registry;
    }

    public List<MatchResult> match(SwapRequest mine, Timetable myTimetable) {
        List<MatchResult> results = new ArrayList<>();
        for (SwapRequest other : board.waitingExcept(mine.getStudentId())) {
            // 1) 교차 일치: 상대가 내놓는 과목명 = 내 희망, 상대 희망 = 내가 내놓는 과목명
            boolean crossMatch = other.getGiveCourse().getName().equals(mine.getWantName())
                    && other.getWantName().equals(mine.getGiveCourse().getName());
            if (!crossMatch) {
                continue; // 조건 불일치 -> 후보 아님
            }
            Course receive = other.getGiveCourse(); // 내가 받을 과목(상대 분반의 실제 시간)

            // 2) 내 시간표 겹침 (내놓는 과목 자리는 비므로 제외하고 비교)
            Optional<Course> mineConflict = myTimetable.findConflict(receive, mine.getGiveCourse());
            if (mineConflict.isPresent()) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE,
                        "겹침: " + mineConflict.get().getName()));
                continue;
            }

            // 3) 상대 시간표 겹침: 상대가 내 과목을 받았을 때 (R5-2). 상대 시간표가 없으면 내 쪽만 검사(16절 Q8)
            Optional<Student> partner = registry.find(other.getStudentId());
            if (partner.isEmpty()) {
                results.add(MatchResult.possible(other, receive, "겹침 없음 (상대 시간표 미확인)"));
                continue;
            }
            Optional<Course> partnerConflict =
                    partner.get().getTimetable().findConflict(mine.getGiveCourse(), receive);
            if (partnerConflict.isPresent()) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_PARTNER,
                        "상대 시간표 겹침: " + partnerConflict.get().getName()));
                continue;
            }
            results.add(MatchResult.possible(other, receive));
        }
        return results;
    }
}
