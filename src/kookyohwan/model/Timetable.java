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
     * R2: 시간표 추가. 이미 있거나 겹치면 false를 돌려주고 시간표는 그대로 둔다.
     * (겹칠 때 교체 여부는 콘솔이 묻고, y면 replace를 호출)
     */
    public boolean add(Course course) {
        if (contains(course.getCode()) || !findConflicts(course, null).isEmpty()) {
            return false;
        }
        courses.add(course);
        return true;
    }

    /** R2 교체: 겹치는 과목들을 빼고 새 과목을 넣는다. 사용자가 y라고 답한 뒤에만 호출. */
    public void replace(List<Course> toRemove, Course course) {
        courses.removeAll(toRemove);
        courses.add(course);
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
