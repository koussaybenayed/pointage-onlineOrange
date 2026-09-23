package com.pointage.ticketreport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class ExcelUploadController {

    private final String uploadPath;
    private final TicketReportService ticketReportService;

    public ExcelUploadController(@Value("${app.upload-path:../uploads}") String uploadPath,
                                 TicketReportService ticketReportService) {
        this.uploadPath = uploadPath;
        this.ticketReportService = ticketReportService;
    }

    @PostMapping("/upload-excel")
    public Map<String, String> upload(@RequestParam("files") List<MultipartFile> files,
                                      @RequestParam(required = false) String reportType) throws IOException {
        StringBuilder saved = new StringBuilder();
        for (MultipartFile file : files) {
            String fileName = resolveFileName(file.getOriginalFilename(), reportType);
            Path target = Path.of(uploadPath, fileName);
            Files.createDirectories(target.getParent());

            Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            try {
                Files.copy(file.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(target);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.FileSystemException e) {
                    Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
            if (saved.length() > 0) saved.append(", ");
            saved.append(fileName);
        }

        ticketReportService.clearCache();
        return Map.of("status", "ok", "file", saved.toString());
    }

    /**
     * Identifies each uploaded report by its file name (SMC_BO / ATP / TDB),
     * with the explicit reportType param as a fallback when nothing matches.
     */
    private String resolveFileName(String originalName, String reportType) {
        String name = originalName == null ? "" : originalName.toLowerCase();

        if (name.contains("smc") && name.contains("tdb")) return "TDBRapport_SMC_BO.xlsx";
        if (name.contains("tdb")) return "TDBRapport_SMC_BO.xlsx";
        if (name.contains("smc")) return "Rapport_SMC_BO.xlsx";
        if (name.contains("atp")) return "Rapport ATP_WO_355_4225286481794306943.xlsx";

        if (reportType != null) {
            return switch (reportType) {
                case "SMC_BO" -> "Rapport_SMC_BO.xlsx";
                case "ATP_WO" -> "Rapport ATP_WO_355_4225286481794306943.xlsx";
                case "TDB_SMC_BO" -> "TDBRapport_SMC_BO.xlsx";
                default -> "Rapport_" + name;
            };
        }
        return "Rapport_" + name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
