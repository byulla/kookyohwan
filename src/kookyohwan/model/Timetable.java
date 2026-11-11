package kookyohwan.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 한 학생의 시간표. 겹침 판정은 여기 한 곳에만 둔다 —
 * R2(추가 거절), R5(매칭 제외), R5-2(상대 시간표)가 전부 findConflicts를 호출한다.
 */
public class Timetable {
    /** 한 학기에 담을 수 있는 최대 학점. 넘으면 추가·교체를 거절하고, 매칭에서도 교환 후 넘는 상대는 제외한다. */
    public static final int MAX_CREDITS = 18;

    private final List<Course> courses = new ArrayList<>();

    /**
     * incoming을 넣었을 때 겹치는 과목 전부.
     * giving(내놓을 과목)은 그 자리가 비므로 비교에서 제외한다(AC2). null이면 제외 없음.
     */
    public List<Course> findConflicts(Course incoming, Course giving) {
        List<Course> result = new ArrayList<>();
        for (Course mine : courses) {
            if (giving != null && mine.getCode().equals(giving.getCode())) {
                continue;
            }
            if (mine.overlaps(incoming)) {
                result.add(mine);
            }
        }
        return result;
    }

    /** 매칭 사유 출력용: 첫 번째 겹치는 과목만. */
    public Optional<Course> findConflict(Course incoming, Course giving) {
        List<Course> conflicts = findConflicts(incoming, giving);
        return conflicts.isEmpty() ? Optional.empty() : Optional.of(conflicts.get(0));
    }

    /**
     * 중복 수강 판정은 여기 한 곳: removing을 뺀 나머지 중에 incoming과 같은 과목(같은 분반 포함)이 있으면 그 과목.
     * 시간이 안 겹치는 다른 분반이나 시간이 없는 과목은 겹침 검사로는 못 잡아서 따로 본다.
     */
    public Optional<Course> findSameSubject(Course incoming, List<Course> removing) {
        for (Course mine : courses) {
            if (mine.isSameSubject(incoming) && !containsCode(removing, mine.getCode())) {
                return Optional.of(mine);
            }
        }
        return Optional.empty();
    }

    private static boolean containsCode(List<Course> list, String code) {
        for (Course c : list) {
            if (c.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * R2: 시간표 추가. 이미 듣는 과목(다른 분반 포함)이거나, 겹치거나, 최대 학점을 넘으면
     * false를 돌려주고 시간표는 그대로 둔다. (겹칠 때 교체 여부는 콘솔이 묻고, y면 replace를 호출)
     */
    public boolean add(Course course) {
        return replace(Collections.emptyList(), course);
    }

    /**
     * R2 교체·교환: toRemove를 빼고 새 과목을 넣는다. 사용자가 y라고 답한 뒤에만 호출.
     * 빼고 난 뒤에도 겹치는 과목이 남거나, 같은 과목을 이미 듣거나, 최대 학점을 넘으면
     * false를 돌려주고 시간표는 그대로 둔다 — 부르는 쪽이 검사를 빠뜨려도 어긋난 시간표가 만들어지지 않게.
     */
    public boolean replace(List<Course> toRemove, Course course) {
        if (findSameSubject(course, toRemove).isPresent() || exceedsLimit(course, toRemove)) {
            return false;
        }
        for (Course conflict : findConflicts(course, null)) {
            if (!containsCode(toRemove, conflict.getCode())) {
                return false;
            }
        }
        courses.removeIf(c -> containsCode(toRemove, c.getCode()));
        courses.add(course);
        return true;
    }

    /**
     * 복원 전용: 파일에 저장돼 있던 과목을 그대로 넣는다. 같은 분반 중복·겹침만 거절하고
     * 학점 제한과 같은 과목 다른 분반은 보지 않는다 — 규칙을 두기 전에 저장한 시간표도 읽히게 하려고.
     */
    public boolean restore(Course course) {
        if (contains(course.getCode()) || !findConflicts(course, null).isEmpty()) {
            return false;
        }
        courses.add(course);
        return true;
    }

    /** 수강 취소: 과목 하나를 뺀다. 시간표에 없으면 false. (내놓는 중인 과목인지는 부르는 쪽이 RequestBoard에 먼저 묻는다) */
    public boolean remove(Course course) {
        return courses.removeIf(c -> c.getCode().equals(course.getCode()));
    }

    public int getTotalCredits() {
        int total = 0;
        for (Course c : courses) {
            total += c.getCredits();
        }
        return total;
    }

    /** removing을 빼고 incoming을 넣었을 때의 총 학점. 시간표 추가·교체와 매칭(교환 후 학점)이 같이 쓴다. */
    public int creditsAfter(Course incoming, List<Course> removing) {
        int total = getTotalCredits() + incoming.getCredits();
        for (Course c : removing) {
            if (contains(c.getCode())) {
                total -= c.getCredits();
            }
        }
        return total;
    }

    /** 학점 제한 판정은 여기 한 곳. 바꾼 뒤 총 학점이 최대를 넘으면 예외 없이 초과다 (추가·교체·교환 모두). */
    public boolean exceedsLimit(Course incoming, List<Course> removing) {
        return creditsAfter(incoming, removing) > MAX_CREDITS;
    }

    public boolean contains(String code) {
        return findByCode(code).isPresent();
    }

    public Optional<Course> findByCode(String code) {
        for (Course c : courses) {
            if (c.getCode().equals(code)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public List<Course> getCourses() {
        return Collections.unmodifiableList(courses);
    }

    public int size() {
        return courses.size();
    }
}
