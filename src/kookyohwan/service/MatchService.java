package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.CycleMatch;
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
    public static final int CYCLE_SIZE = 3;

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

    public List<CycleMatch> matchCycles(SwapRequest mine, Timetable myTimetable) {
        List<CycleMatch> results = new ArrayList<>();
        List<SwapRequest> path = new ArrayList<>();
        path.add(mine);
        searchCycles(path, board.waitingExcept(mine.getStudentId()), myTimetable, results);
        return results;
    }

    private void searchCycles(List<SwapRequest> path, List<SwapRequest> others, Timetable myTimetable,
                              List<CycleMatch> results) {
        SwapRequest last = path.get(path.size() - 1);
        if (path.size() == CYCLE_SIZE) {
            if (last.getWantName().equals(path.get(0).getGiveCourse().getName())) {
                results.add(judgeCycle(path, myTimetable));
            }
            return;
        }
        for (SwapRequest next : others) {
            if (!next.getGiveCourse().getName().equals(last.getWantName())
                    || hasStudent(path, next.getStudentId())) {
                continue;
            }
            path.add(next);
            searchCycles(path, others, myTimetable, results);
            path.remove(path.size() - 1);
        }
    }

    private static boolean hasStudent(List<SwapRequest> path, String studentId) {
        for (SwapRequest r : path) {
            if (r.getStudentId().equals(studentId)) {
                return true;
            }
        }
        return false;
    }

    private CycleMatch judgeCycle(List<SwapRequest> path, Timetable myTimetable) {
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            SwapRequest r = path.get(i);
            Course receive = path.get((i + 1) % path.size()).getGiveCourse();
            Timetable table = myTimetable;
            if (i > 0) {
                Optional<Student> student = registry.find(r.getStudentId());
                if (student.isEmpty()) {
                    notes.add("#" + r.getId() + " 시간표 미확인");
                    continue;
                }
                table = student.get().getTimetable();
            }
            Optional<String> problem = cycleProblem(table, receive, r.getGiveCourse());
            if (problem.isPresent()) {
                return CycleMatch.excluded(path, i == 0 ? MatchVerdict.EXCLUDED_MINE : MatchVerdict.EXCLUDED_PARTNER,
                        (i == 0 ? "내 " : "#" + r.getId() + " ") + problem.get());
            }
        }
        return CycleMatch.possible(path, notes.isEmpty() ? "겹침 없음" : "겹침 없음 (" + String.join(", ", notes) + ")");
    }

    private Optional<String> cycleProblem(Timetable table, Course incoming, Course leaving) {
        if (!table.contains(leaving.getCode())) {
            return Optional.of("시간표에 내놓는 과목이 없음");
        }
        Optional<Course> same = table.findSameSubject(incoming, Collections.singletonList(leaving));
        if (same.isPresent()) {
            return Optional.of("시간표에 이미 있는 과목: " + same.get().getName() + " " + same.get().getSection());
        }
        Optional<Course> conflict = table.findConflict(incoming, leaving);
        if (conflict.isPresent()) {
            return Optional.of("시간표 겹침: " + conflict.get().getName());
        }
        if (table.exceedsLimit(incoming, Collections.singletonList(leaving))) {
            return Optional.of("학점 초과: 교환 후 " + table.creditsAfter(incoming, Collections.singletonList(leaving))
                    + "학점 (최대 " + Timetable.MAX_CREDITS + ")");
        }
        return Optional.empty();
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
