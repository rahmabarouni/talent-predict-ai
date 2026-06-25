package com.talentpredict.modules.evaluation.entities;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.talentpredict.modules.user.entities.User;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tests_personnalite")
public class PersonalityTest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // infos
    @Column(name = "date_test", nullable = false)
    @Builder.Default
    private LocalDateTime dateTest = LocalDateTime.now();

    @Column(name = "type_test", length = 50)
    private String typeTest; // PCM, MBTI, Big Five, DISC, etc.

    @ElementCollection
    @CollectionTable(name = "test_reponses", joinColumns = @JoinColumn(name = "test_id"))
    @MapKeyColumn(name = "question_key")
    @Column(name = "reponse_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> reponses = new HashMap<>();

    @Column(columnDefinition = "TEXT")
    private String resultats;

    @Column(name = "analyse_llm", columnDefinition = "TEXT")
    private String analyseLlm; // Analyse générée par OpenAI

    @Column
    private Integer score;

    // relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private User user;
}
