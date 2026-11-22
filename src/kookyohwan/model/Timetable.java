package kookyohwan.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Timetable {
    public static final int MAX_CREDITS = 18;

    private final List<Course> courses = new ArrayList<>();

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

    public Optional<Course> findConflict(Course incoming, Course giving) {
        List<Course> conflicts = findConflicts(incoming, giving);
        return conflicts.isEmpty() ? Optional.empty() : Optional.of(conflicts.get(0));
    }

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

    public boolean add(Course course) {
        return replace(Collections.emptyList(), course);
    }

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

    public boolean restore(Course course) {
        if (contains(course.getCode()) || !findConflicts(course, null).isEmpty()) {
            return false;
        }
        courses.add(course);
        return true;
    }

    public boolean remove(Course course) {
        return courses.removeIf(c -> c.getCode().equals(course.getCode()));
    }

    public int getTotalCredits() {
        long total = 0;
        for (Course c : courses) {
            total += c.getCredits();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    public int creditsAfter(Course incoming, List<Course> removing) {
        long total = incoming.getCredits();
        for (Course c : courses) {
            if (!containsCode(removing, c.getCode())) {
                total += c.getCredits();
            }
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

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
