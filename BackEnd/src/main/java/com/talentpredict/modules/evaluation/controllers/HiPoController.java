package com.talentpredict.modules.evaluation.controllers;

import com.talentpredict.modules.evaluation.dto.HiPoDto;
import com.talentpredict.modules.evaluation.services.HiPoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/evaluation/hipo")
@RequiredArgsConstructor
public class HiPoController {

    private final HiPoService hiPoService;

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<HiPoDto> getUserHiPo(@PathVariable UUID userId) {
        return ResponseEntity.ok(hiPoService.calculateHiPoForUser(userId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<HiPoDto>> getAllUsersHiPo() {
        return ResponseEntity.ok(hiPoService.calculateHiPoForAllUsers());
    }
}
