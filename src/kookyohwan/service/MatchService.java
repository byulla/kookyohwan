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

public class MatchService {
    private final RequestBoard board;
    private final StudentRegistry registry;

    public MatchService(RequestBoard board, StudentRegistry registry) {
        this.board = board;
        this.registry = registry;
    }

    public List<MatchResult> match(SwapRequest mine, Timetable myTimetable) {
        List<MatchResult> results = new ArrayList<>();
        for (SwapRequest other : board.waitingExcept(mine.getStudentId())) {
            boolean crossMatch = other.getGiveCourse().getName().equals(mine.getWantName())
                    && other.getWantName().equals(mine.getGiveCourse().getName());
            if (!crossMatch) {
                continue;
            }
            Course receive = other.getGiveCourse();

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

            Optional<Course> mineSame = myTimetable.findSameSubject(receive,
                    Collections.singletonList(mine.getGiveCourse()));
            if (mineSame.isPresent()) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE,
                        "내가 이미 듣는 과목: " + mineSame.get().getName() + " " + mineSame.get().getSection()));
                continue;
            }

            List<Course> conflicts = myTimetable.findConflicts(receive, mine.getGiveCourse());
            List<Course> leaving = new ArrayList<>();
            leaving.add(mine.getGiveCourse());
            leaving.addAll(conflicts);
            if (!conflicts.isEmpty()) {
                String reason = "내 시간표 겹침: " + conflicts.get(0).getName();

                boolean droppable = !myTimetable.exceedsLimit(receive, leaving)
                        && partnerProblem(mine, other).isEmpty();
                for (Course c : conflicts) {
                    droppable &= !board.isGiving(mine.getStudentId(), c.getCode());
                }
                results.add(droppable ? MatchResult.resolvable(other, receive, reason, conflicts)
                        : MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE, reason));
                continue;
            }

            if (myTimetable.exceedsLimit(receive, leaving)) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_MINE,
                        "내 학점 초과: 교환 후 " + myTimetable.creditsAfter(receive, leaving)
                                + "학점 (최대 " + Timetable.MAX_CREDITS + ")"));
                continue;
            }
            List<String> notes = new ArrayList<>();

            if (!myTimetable.findConflicts(receive, null).isEmpty()) {
                notes.add(mine.getGiveCourse().getName() + " 자리");
            }

            if (registry.find(other.getStudentId()).isEmpty()) {
                notes.add("상대 시간표 미확인");
            }
            Optional<String> problem = partnerProblem(mine, other);
            if (problem.isPresent()) {
                results.add(MatchResult.excluded(other, receive, MatchVerdict.EXCLUDED_PARTNER, problem.get()));
                continue;
            }
            String reason = notes.isEmpty() ? "겹침 없음" : "겹침 없음 (" + String.join(", ", notes) + ")";
            results.add(MatchResult.possible(other, receive, reason));
        }
        return results;
    }

    private Optional<String> partnerProblem(SwapRequest mine, SwapRequest other) {
        Optional<Student> partner = registry.find(other.getStudentId());
        if (partner.isEmpty()) {
            return Optional.empty();
        }
        Timetable theirs = partner.get().getTimetable();
        Course incoming = mine.getGiveCourse();
        Course leaving = other.getGiveCourse();
        Optional<Course> same = theirs.findSameSubject(incoming, Collections.singletonList(leaving));
        if (same.isPresent()) {
            return Optional.of("상대가 이미 듣는 과목: " + same.get().getName() + " " + same.get().getSection());
        }
        Optional<Course> conflict = theirs.findConflict(incoming, leaving);
        if (conflict.isPresent()) {
            return Optional.of("상대 시간표 겹침: " + conflict.get().getName());
        }
        if (theirs.exceedsLimit(incoming, Collections.singletonList(leaving))) {
            return Optional.of("상대 학점 초과: 교환 후 "
                    + theirs.creditsAfter(incoming, Collections.singletonList(leaving))
                    + "학점 (최대 " + Timetable.MAX_CREDITS + ")");
        }
        return Optional.empty();
    }
}
