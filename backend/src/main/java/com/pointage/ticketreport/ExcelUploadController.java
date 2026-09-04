package com.pointage.ticketreport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class ExcelUploadController {

    private final String uploadPath;

    public ExcelUploadController(@Value("${app.upload-path:../uploads}") String uploadPath) {
        this.uploadPath = uploadPath;
    }

    @PostMapping("/upload-excel")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam String team,
                                      @RequestParam String reportType) throws IOException {
        String fileName = resolveFileName(team, reportType);
        Path target = Path.of(uploadPath, fileName);
        Files.createDirectories(target.getParent());

        // Read upload entirely into memory first, then release the temp
        Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
        try {
            Files.copy(file.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
            // Delete only if not locked; then move
            Files.deleteIfExists(target);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.FileSystemException e) {
                Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return Map.of("status", "ok", "file", fileName);
    }

    private String resolveFileName(String team, String reportType) {
        return switch (reportType) {
            case "SMC_BO" -> "Rapport_SMC_BO.xlsx";
            case "ATP_WO" -> "Rapport ATP_WO_355_4225286481794306943.xlsx";
            case "TDB_SMC_BO" -> "TDBRapport_SMC_BO.xlsx";
            default -> "Rapport_" + team + "_" + reportType + ".xlsx";
        };
    }
}
