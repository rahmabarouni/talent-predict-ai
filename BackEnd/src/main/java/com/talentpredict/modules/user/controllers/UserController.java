package com.talentpredict.modules.user.controllers;

import com.talentpredict.modules.user.dto.UserDto;
import com.talentpredict.modules.user.dto.UserSummaryDto;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.services.IUserService;
import com.talentpredict.modules.auth.dto.AuthDto;
import com.talentpredict.modules.auth.services.IAuthService;
import com.talentpredict.modules.formation.entities.Formation;
import com.talentpredict.modules.formation.repositories.FormationRepository;
import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.ai.repositories.PredictionRepository;
import com.talentpredict.modules.user.repositories.ProfileRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final IUserService accountService;
    private final IAuthService authService;
    private final FormationRepository formationRepository;
    private final PredictionRepository predictionRepository;
    private final ProfileRepository profileRepository;

    /**
     * GET /api/users — List all users.
     * Admin only.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> listUsers() {
        log.info("Admin request: list all users");
        return ResponseEntity.ok(accountService.listUsers());
    }

    /**
     * GET /api/users/leaderboard — List top 10 users by XP.
     * Accessible by USER and ADMIN.
     */
    @GetMapping("/leaderboard")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<UserDto.LeaderboardResponse>> getLeaderboard() {
        log.info("Request for gamification leaderboard");
        return ResponseEntity.ok(accountService.getLeaderboard());
    }

    /**
     * POST /api/users — Create a new user (Admin only).
     * BUG FIX: this endpoint was missing despite SecurityConfig referencing it.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserDto.CreateRequest request) {
        log.info("Admin creating new user for email: {}", request.getEmail());
        var authRequest = new AuthDto.RegisterRequest();
        authRequest.setFirstName(request.getFirstName());
        authRequest.setLastName(request.getLastName());
        authRequest.setEmail(request.getEmail());
        authRequest.setPassword(request.getPassword());
        User created = authService.createUser(authRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/users/{userId} — Get user by ID.
     * Ownership check enforced in service (can only view own user unless ADMIN).
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<User> getUserById(
            @PathVariable UUID userId,
            @AuthenticationPrincipal(expression = "user") User currentUser) {
        log.info("User {} requesting user {}", (currentUser != null ? currentUser.getId() : "anonymous"), userId);
        return ResponseEntity.ok(accountService.getUserById(userId, currentUser));
    }

    /**
     * GET /api/users/{userId}/summary — Returns real aggregated stats for the admin panel.
     * Replaces all mock/randomised data (scoreMoyen, testsCount, etc.) previously generated
     * on the frontend.  Admin only.
     */
    @GetMapping("/{userId}/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummaryDto> getUserSummary(@PathVariable UUID userId) {
        log.info("Admin requesting summary for user {}", userId);

        long total     = formationRepository.countByUserId(userId);
        long enCours   = formationRepository.countByUserIdAndStatut(userId, Formation.StatutFormation.EN_COURS);
        long terminee  = formationRepository.countByUserIdAndStatut(userId, Formation.StatutFormation.TERMINEE);

        var latestPred = predictionRepository.findFirstByUserIdOrderByDatePredictionDesc(userId);
        long predsCount = predictionRepository.findByUserIdOrderByDatePredictionDesc(userId).size();

        var profile = profileRepository.findByUser_Id(userId).orElse(null);
        String github = profile != null ? profile.getGithubUrl() : null;
        String linkedin = profile != null ? profile.getLienLinkedin() : null;

        UserSummaryDto dto = UserSummaryDto.builder()
                .userId(userId)
                .formationsTotal(total)
                .formationsEnCours(enCours)
                .formationsTerminees(terminee)
                .predictionsCount(predsCount)
                .latestPredictionScore(latestPred.map(Prediction::getScoreConfiance).orElse(null))
                .latestPredictionDate(latestPred.map(Prediction::getDatePrediction).orElse(null))
                .latestPredictionLabel(latestPred.map(p -> p.getStatut().name()).orElse(null))
                .githubUrl(github)
                .linkedinUrl(linkedin)
                .build();

        return ResponseEntity.ok(dto);
    }

    /**
     * PUT /api/users/{userId} — Update user details.
     * Ownership check enforced in service.
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<User> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UserDto.UpdateRequest request,
            @AuthenticationPrincipal(expression = "user") User currentUser) {
        log.info("User {} updating user {}", (currentUser != null ? currentUser.getId() : "anonymous"), userId);
        return ResponseEntity.ok(accountService.updateUser(userId, request, currentUser));
    }

    /**
     * DELETE /api/users/{userId} — Delete an user.
     * Admin only (SecurityConfig) + ownership check in service.
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal(expression = "user") User currentUser) {
        log.info("Admin {} deleting user {}", (currentUser != null ? currentUser.getId() : "anonymous"), userId);
        accountService.deleteUser(userId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
