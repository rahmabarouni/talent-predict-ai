package com.talentpredict.modules.user.services;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.talentpredict.modules.user.dto.ProfileDto;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j

public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    private static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    public Profile createProfile(Profile profile) {
        return profileRepository.save(requireNonNull(profile, "profile"));
    }

    /**
     * Update profile by profile UUID (used by ProfileController PUT /{profileId}).
     * Only updates non-null fields.
     */
    @Transactional
    public Profile updateProfile(UUID id, Profile profileDetails) {
        UUID safeId = requireNonNull(id, "id");
        Profile safeProfileDetails = requireNonNull(profileDetails, "profileDetails");
        Profile profile = profileRepository.findById(safeId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found with id: " + safeId));

        if (safeProfileDetails.getTitreProfessionnel() != null)
            profile.setTitreProfessionnel(safeProfileDetails.getTitreProfessionnel());
        if (safeProfileDetails.getDescription() != null)
            profile.setDescription(safeProfileDetails.getDescription());
        if (safeProfileDetails.getUrlPhoto() != null)
            profile.setUrlPhoto(safeProfileDetails.getUrlPhoto());
        if (safeProfileDetails.getExperienceAns() != null)
            profile.setExperienceAns(safeProfileDetails.getExperienceAns());
        if (safeProfileDetails.getNiveauEtudes() != null)
            profile.setNiveauEtudes(safeProfileDetails.getNiveauEtudes());
        if (safeProfileDetails.getLienLinkedin() != null)
            profile.setLienLinkedin(safeProfileDetails.getLienLinkedin());
        if (safeProfileDetails.getGithubUrl() != null)
            profile.setGithubUrl(safeProfileDetails.getGithubUrl());
        if (safeProfileDetails.getCvUrl() != null)
            profile.setCvUrl(safeProfileDetails.getCvUrl());
        if (safeProfileDetails.getPortfolioUrl() != null)
            profile.setPortfolioUrl(safeProfileDetails.getPortfolioUrl());

        return profileRepository.save(requireNonNull(profile, "profile"));
    }

    /**
     * TASK 3: Update profile by userId using ProfileDto.UpdateRequest.
     * Creates the profile if it doesn't exist yet (upsert behavior).
     */
    @Transactional
    public ProfileDto.Response updateProfileByAccountId(UUID userId, ProfileDto.UpdateRequest request) {
        UUID safeUserId = requireNonNull(userId, "userId");
        ProfileDto.UpdateRequest safeRequest = requireNonNull(request, "request");
        User account = userRepository.findById(safeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + safeUserId));

        Profile profile = profileRepository.findByUser_Id(safeUserId)
                .orElseGet(() -> {
                    log.info("No profile found for userId={} - creating new one", safeUserId);
                    Profile newProfile = new Profile();
                    newProfile.setUser(account);
                    return newProfile;
                });

        // Update only non-null fields
        if (safeRequest.getTitreProfessionnel() != null)
            profile.setTitreProfessionnel(safeRequest.getTitreProfessionnel());
        if (safeRequest.getDescription() != null)
            profile.setDescription(safeRequest.getDescription());
        if (safeRequest.getUrlPhoto() != null)
            profile.setUrlPhoto(safeRequest.getUrlPhoto());
        if (safeRequest.getExperienceAns() != null)
            profile.setExperienceAns(safeRequest.getExperienceAns());
        if (safeRequest.getNiveauEtudes() != null)
            profile.setNiveauEtudes(safeRequest.getNiveauEtudes());
        if (safeRequest.getLienLinkedin() != null)
            profile.setLienLinkedin(safeRequest.getLienLinkedin());
        if (safeRequest.getGithubUrl() != null)
            profile.setGithubUrl(safeRequest.getGithubUrl());
        if (safeRequest.getCvUrl() != null)
            profile.setCvUrl(safeRequest.getCvUrl());
        if (safeRequest.getPortfolioUrl() != null)
            profile.setPortfolioUrl(safeRequest.getPortfolioUrl());

        Profile saved = profileRepository.save(requireNonNull(profile, "profile"));
        log.info("Profile updated for userId={}", safeUserId);
        return toResponse(saved, account);
    }

    /**
     * TASK 3: Get profile by userId and return enriched DTO with user info.
     */
    @Transactional
    public ProfileDto.Response getProfileByAccountId(UUID userId) {
        UUID safeUserId = requireNonNull(userId, "userId");
        User account = userRepository.findById(safeUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + safeUserId));

        Profile profile = profileRepository.findByUser_Id(safeUserId)
                .orElseGet(() -> {
                    // Create and persist profile so response always has a valid id
                    Profile empty = new Profile();
                    empty.setUser(account);
                return profileRepository.save(requireNonNull(empty, "profile"));
                });

        return toResponse(profile, account);
    }

    private ProfileDto.Response toResponse(Profile profile, User account) {
        ProfileDto.Response response = new ProfileDto.Response();
        response.setId(profile.getId());
        response.setUserId(account.getId());
        // Read-only user fields
        response.setFirstName(account.getFirstName());
        response.setLastName(account.getLastName());
        response.setEmail(account.getEmail());
        response.setPosition(account.getPosition());
        response.setDepartment(account.getDepartment());
        // Editable profile fields
        response.setTitreProfessionnel(profile.getTitreProfessionnel());
        response.setDescription(profile.getDescription());
        response.setUrlPhoto(profile.getUrlPhoto());
        response.setExperienceAns(profile.getExperienceAns());
        response.setNiveauEtudes(profile.getNiveauEtudes());
        response.setLienLinkedin(profile.getLienLinkedin());
        response.setGithubUrl(profile.getGithubUrl());
        response.setCvUrl(profile.getCvUrl());
        response.setPortfolioUrl(profile.getPortfolioUrl());
        // GitHub stats
        response.setGithubRepos(profile.getGithubRepos());
        response.setGithubFollowers(profile.getGithubFollowers());
        response.setGithubFollowing(profile.getGithubFollowing());
        response.setGithubBio(profile.getGithubBio());
        response.setGithubCompany(profile.getGithubCompany());
        response.setGithubLocation(profile.getGithubLocation());
        response.setGithubAvatarUrl(profile.getGithubAvatarUrl());
        response.setGithubName(profile.getGithubName());
        response.setAiSummary(profile.getAiSummary());
        response.setPublicSlug(profile.getPublicSlug());
        return response;
    }

    /**
     * Explicitly publish a profile by generating a public slug if one doesn't exist.
     */
    @Transactional
    public ProfileDto.Response publishProfile(UUID userId) {
        UUID safeUserId = requireNonNull(userId, "userId");
        User account = userRepository.findById(safeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + safeUserId));

        Profile profile = profileRepository.findByUser_Id(safeUserId)
                .orElseGet(() -> {
                    Profile empty = new Profile();
                    empty.setUser(account);
                    return profileRepository.save(requireNonNull(empty, "profile"));
                });

        if (profile.getPublicSlug() == null || profile.getPublicSlug().isBlank()) {
            profile.setPublicSlug("p-" + UUID.randomUUID().toString().substring(0, 8));
            profile = profileRepository.save(profile);
            log.info("Generated public slug for userId={}: {}", safeUserId, profile.getPublicSlug());
        }

        return toResponse(profile, account);
    }

    public Profile getProfileById(UUID id) {
        UUID safeId = requireNonNull(id, "id");
        return profileRepository.findById(safeId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found with id: " + safeId));
    }

    public Optional<Profile> getProfileByUser(User account) {
        return profileRepository.findByUser(requireNonNull(account, "account"));
    }

    public List<Profile> getAllProfiles() {
        return profileRepository.findAll();
    }

    public void deleteProfile(UUID targetProfileId) {
        profileRepository.deleteById(requireNonNull(targetProfileId, "targetProfileId"));
    }

    /**
     * Update GitHub stats and AI summary on the profile (called by analysis orchestrator).
     */
    @Transactional
    public void updateGithubStats(UUID userId, Integer repos, Integer followers, Integer following,
                                   String bio, String company, String location, String avatarUrl,
                                   String name, String aiSummary) {
        UUID safeUserId = requireNonNull(userId, "userId");
        Profile profile = profileRepository.findByUser_Id(safeUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile not found for userId: " + safeUserId));

        if (repos != null) profile.setGithubRepos(repos);
        if (followers != null) profile.setGithubFollowers(followers);
        if (following != null) profile.setGithubFollowing(following);
        if (bio != null) profile.setGithubBio(bio);
        if (company != null) profile.setGithubCompany(company);
        if (location != null) profile.setGithubLocation(location);
        if (avatarUrl != null) profile.setGithubAvatarUrl(avatarUrl);
        if (name != null) profile.setGithubName(name);
        if (aiSummary != null) profile.setAiSummary(aiSummary);

        profileRepository.save(requireNonNull(profile, "profile"));
        log.info("GitHub stats updated for userId={}", safeUserId);
    }
}
