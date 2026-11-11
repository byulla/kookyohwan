package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.MatchResult;
import kookyohwan.model.MatchVerdict;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.model.Timetable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * R4 + R5 + R5-2 매칭. 조회 전용 — 요청 상태도 시간표도 바꾸지 않는다.
 * 시간 비교 코드는 여기 없다. 겹침 판정은 전부 Timetable.findConflicts에 맡긴다.
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

            // 1-2) 내놓는 과목이 실제로 시간표에 있어야 교환이 된다. 파일을 손으로 고쳐 어긋난 요청은 제외
            if (!myTimetable.contains(mine.getGiveCourse().getCode())) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE,
                        "내놓는 과목이 내 시간표에 없음"));
                continue;
            }
            Optional<Student> giver = registry.find(other.getStudentId());
            if (giver.isPresent() && !giver.get().getTimetable().contains(receive.getCode())) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_PARTNER,
                        "상대가 내놓는 과목이 상대 시간표에 없음"));
                continue;
            }

            // 1-3) 중복 수강: 내가 받을 과목을 이미 듣고 있으면(다른 분반 포함) 제외. 판정은 Timetable 한 곳
            Optional<Course> mineSame = myTimetable.findSameSubject(receive,
                    Collections.singletonList(mine.getGiveCourse()));
            if (mineSame.isPresent()) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE,
                        "내가 이미 듣는 과목: " + mineSame.get().getName() + " " + mineSame.get().getSection()));
                continue;
            }

            // 2) 내 시간표 겹침 (내놓는 과목 자리는 비므로 제외하고 비교)
            Optional<Course> mineConflict = myTimetable.findConflict(receive, mine.getGiveCourse());
            if (mineConflict.isPresent()) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE,
                        "내 시간표 겹침: " + mineConflict.get().getName()));
                continue;
            }
            // 2-2) 교환 후 내 학점이 최대 학점을 넘으면 정정이 안 되므로 제외 (학점 판정도 Timetable 한 곳)
            if (myTimetable.exceedsLimit(receive, Collections.singletonList(mine.getGiveCourse()))) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE,
                        "내 학점 초과: 교환 후 "
                                + myTimetable.creditsAfter(receive, Collections.singletonList(mine.getGiveCourse()))
                                + "학점 (최대 " + Timetable.MAX_CREDITS + ")"));
                continue;
            }
            List<String> notes = new ArrayList<>();
            // 내놓는 과목을 빼지 않고 다시 물었을 때 겹치면, 그 과목 자리로 들어간다는 뜻(AC2) -> 사유에 남긴다
            if (!myTimetable.findConflicts(receive, null).isEmpty()) {
                notes.add(mine.getGiveCourse().getName() + " 자리");
            }

            // 3) 상대 시간표 겹침: 상대가 내 과목을 받았을 때 (R5-2). 상대 시간표가 없으면 내 쪽만 검사(16절 Q8)
            Optional<Student> partner = registry.find(other.getStudentId());
            if (partner.isEmpty()) {
                notes.add("상대 시간표 미확인");
            } else {
                Optional<Course> partnerSame = partner.get().getTimetable().findSameSubject(
                        mine.getGiveCourse(), Collections.singletonList(receive));
                if (partnerSame.isPresent()) {
                    results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_PARTNER,
                            "상대가 이미 듣는 과목: " + partnerSame.get().getName() + " "
                                    + partnerSame.get().getSection()));
                    continue;
                }
                Optional<Course> partnerConflict =
                        partner.get().getTimetable().findConflict(mine.getGiveCourse(), receive);
                if (partnerConflict.isPresent()) {
                    results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_PARTNER,
                            "상대 시간표 겹침: " + partnerConflict.get().getName()));
                    continue;
                }
                Timetable theirs = partner.get().getTimetable();
                if (theirs.exceedsLimit(mine.getGiveCourse(), Collections.singletonList(receive))) {
                    results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_PARTNER,
                            "상대 학점 초과: 교환 후 "
                                    + theirs.creditsAfter(mine.getGiveCourse(), Collections.singletonList(receive))
                                    + "학점 (최대 " + Timetable.MAX_CREDITS + ")"));
                    continue;
                }
            }
            String reason = notes.isEmpty() ? "겹침 없음" : "겹침 없음 (" + String.join(", ", notes) + ")";
            results.add(MatchResult.possible(other, receive, reason));
        }
        return results;
    }
}
