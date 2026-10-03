package com.mockinterview.domain.review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
@Entity @Getter @Setter
public class ReviewTask {
    // One task per stable knowledge point, regardless of how long it is overdue.
    @Id @Column(length=100) private String id;
    @Column(nullable=false) private LocalDate dueDate;
    @Column(nullable=false) private String status;
    @Column(length=36) private String evidenceId;
}
