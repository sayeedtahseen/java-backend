package com.sayeed.lender_training_portal.course;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sayeed.lender_training_portal.common.BusinessRuleException;
import com.sayeed.lender_training_portal.user.AppUser;

import jakarta.persistence.*;

@Entity 
@Table(name="course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id")
    private AppUser trainer;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CourseModule> modules = new ArrayList<>();

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    protected Course() { }

    public Course(String title, String description, AppUser trainer, Instant createdAt) {
        this.title = title;
        this.description = description;
        this.trainer = trainer;
        this.createdAt = createdAt;
        this.status = CourseStatus.DRAFT;
    }

    public void addModule(String title, String content, int durationMinutes) {
        modules.add(new CourseModule(this, title, content, modules.size() + 1, durationMinutes));
    }

    public void publish() {
        if (status == CourseStatus.PUBLISHED) {
            throw new BusinessRuleException("Course is already published");
        }
        if (modules.isEmpty()) {
            throw new BusinessRuleException("A course needs at least one module before it can be published");
        }
        status = CourseStatus.PUBLISHED;
    }

    public boolean isOwnedBy(Long userId) {
        return trainer.getId().equals(userId);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public CourseStatus getStatus() { return status; }
    public AppUser getTrainer() { return trainer; }
    public List<CourseModule> getModules() { return Collections.unmodifiableList(modules); }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
