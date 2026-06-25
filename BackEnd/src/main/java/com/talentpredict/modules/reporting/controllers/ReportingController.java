package com.talentpredict.modules.reporting.controllers;

import com.talentpredict.modules.reporting.services.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reporting")
@RequiredArgsConstructor
public class ReportingController {

    private final ExportService exportService;

    @GetMapping("/talent-passport/{userId}")
    public ResponseEntity<byte[]> downloadTalentPassport(@PathVariable UUID userId) {
        byte[] pdfBytes = exportService.generateTalentPassport(userId);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=TalentPassport_" + userId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/hr-global-report")
    public ResponseEntity<byte[]> downloadHrReport() {
        byte[] pdfBytes = exportService.generateHrReport();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=HR_Global_Report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
