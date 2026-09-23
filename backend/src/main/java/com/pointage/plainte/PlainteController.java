package com.pointage.plainte;

import com.pointage.plainte.PlainteDtos.PlainteOverview;
import com.pointage.plainte.PlainteDtos.PlainteSearchResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class PlainteController {

    private final PlainteService plainteService;

    public PlainteController(PlainteService plainteService) {
        this.plainteService = plainteService;
    }

    /**
     * Pie data for the plaintes files. Without a file param the counts are
     * aggregated across every uploaded file; otherwise limited to that file.
     */
    @GetMapping("/plaintes")
    public PlainteOverview overview(@RequestParam(required = false) String file) throws Exception {
        return plainteService.getOverview(file);
    }

    /**
     * Searches plaintes by raison sociale or N° ticket (case-insensitive substring).
     */
    @GetMapping("/plaintes/search")
    public List<PlainteSearchResult> search(
            @RequestParam(required = false) String file,
            @RequestParam(required = false) String q) throws Exception {
        return plainteService.search(file, q);
    }

    /**
     * Uploads one or more plaintes files (e.g. "Plaintes Juillet 2026.xlsx").
     * Each file is kept under its original name so several months can coexist.
     */
    @PostMapping("/upload-plaintes")
    public Map<String, String> upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        Path dir = plainteService.getPlaintesDir();
        Files.createDirectories(dir);

        StringBuilder saved = new StringBuilder();
        for (MultipartFile file : files) {
            String fileName = sanitize(file.getOriginalFilename());
            if (fileName.isEmpty()) {
                continue;
            }
            Path target = dir.resolve(fileName);

            Path tmp = Files.createTempFile(dir, "upload-", ".tmp");
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
            if (saved.length() > 0) {
                saved.append(", ");
            }
            saved.append(fileName);
        }

        plainteService.clearCache();
        return Map.of("status", "ok", "file", saved.toString());
    }

    private String sanitize(String name) {
        if (name == null) {
            return "";
        }
        String clean = Path.of(name).getFileName().toString().replaceAll("[\\\\/]", "");
        return clean;
    }
}