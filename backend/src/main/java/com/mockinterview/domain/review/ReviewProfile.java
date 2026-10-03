package com.mockinterview.domain.review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Getter @Setter
public class ReviewProfile {
    @Id private String id;
    @Column(nullable=false,columnDefinition="LONGTEXT") private String preferencesJson;
}
