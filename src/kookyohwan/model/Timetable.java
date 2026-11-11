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
     * R2: 시간표 추가. 이미 있거나, 겹치거나, 최대 학점을 넘으면 false를 돌려주고 시간표는 그대로 둔다.
     * (겹칠 때 교체 여부는 콘솔이 묻고, y면 replace를 호출)
     */
    public boolean add(Course course) {
        if (contains(course.getCode()) || !findConflicts(course, null).isEmpty()
                || exceedsLimit(course, Collections.emptyList())) {
            return false;
        }
        courses.add(course);
        return true;
    }

    /**
     * R2 교체: 겹치는 과목들을 빼고 새 과목을 넣는다. 사용자가 y라고 답한 뒤에만 호출.
     * 교체한 뒤 최대 학점을 넘으면 false를 돌려주고 시간표는 그대로 둔다.
     */
    public boolean replace(List<Course> toRemove, Course course) {
        if (exceedsLimit(course, toRemove)) {
            return false;
        }
        courses.removeAll(toRemove);
        courses.add(course);
        return true;
    }

    /**
     * 복원 전용: 파일에 저장돼 있던 과목을 그대로 넣는다. 중복·겹침만 거절하고 학점 제한은 보지 않는다 —
     * 제한을 두기 전에 저장한 시간표가 18학점을 넘어도 프로그램이 시작되게 하려고. (넘은 상태에서는 더 추가할 수 없다)
     */
    public boolean restore(Course course) {
        if (contains(course.getCode()) || !findConflicts(course, null).isEmpty()) {
            return false;
        }
        courses.add(course);
        return true;
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

    /**
     * 학점 제한 판정은 여기 한 곳. 바꾼 뒤 총 학점이 최대를 넘고, 지금보다 늘어날 때만 초과로 본다 —
     * 제한 전에 저장돼 이미 넘어 있는 시간표도 학점이 늘지 않는 교체·교환은 할 수 있게.
     */
    public boolean exceedsLimit(Course incoming, List<Course> removing) {
        int after = creditsAfter(incoming, removing);
        return after > MAX_CREDITS && after > getTotalCredits();
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
