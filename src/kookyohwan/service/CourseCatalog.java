package kookyohwan.service;

import kookyohwan.model.Course;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CourseCatalog {
    private final Repository<String, Course> courses = new Repository<>();

    public void add(Course course) {
        courses.put(course.getCode(), course);
    }

    public List<Course> all() {
        return courses.all();
    }

    public int size() {
        return courses.size();
    }

    public Optional<Course> findByCode(String code) {
        return courses.find(code);
    }

    public List<Course> findByName(String name) {
        List<Course> result = new ArrayList<>();
        for (Course c : courses.all()) {
            if (c.getName().equals(name)) {
                result.add(c);
            }
        }
        return result;
    }

    public List<Course> search(String keyword) {
        List<Course> result = new ArrayList<>();
        String key = keyword.toLowerCase(Locale.ROOT);
        for (Course c : courses.all()) {
            if (c.getName().toLowerCase(Locale.ROOT).contains(key)
                    || c.getCode().toLowerCase(Locale.ROOT).startsWith(key)
                    || c.getProfessor().toLowerCase(Locale.ROOT).contains(key)) {
                result.add(c);
            }
        }
        return result;
    }

    public boolean existsName(String name) {
        return !findByName(name).isEmpty();
    }

    public Optional<String> resolveName(String input) {
        Optional<Course> byCode = findByCode(input);
        if (byCode.isPresent()) {
            return Optional.of(byCode.get().getName());
        }
        return existsName(input) ? Optional.of(input) : Optional.empty();
    }
}
