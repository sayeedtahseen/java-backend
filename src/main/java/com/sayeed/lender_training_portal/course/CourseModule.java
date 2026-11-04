package com.sayeed.lender_training_portal.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity 
@Table(name="course_module")
public class CourseModule {
     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String content;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private int durationMinutes;

    protected CourseModule() { }

    CourseModule(Course course, String title, String content, int sortOrder, int durationMinutes) {
        this.course = course;
        this.title = title;
        this.content = content;
        this.sortOrder = sortOrder;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public int getSortOrder() { return sortOrder; }
    public int getDurationMinutes() { return durationMinutes; }
}
