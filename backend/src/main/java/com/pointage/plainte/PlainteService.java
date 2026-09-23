package com.pointage.plainte;

import com.pointage.plainte.PlainteDtos.PlainteFileInfo;
import com.pointage.plainte.PlainteDtos.PlainteOverview;
import com.pointage.plainte.PlainteDtos.PlainteRecurrent;
import com.pointage.plainte.PlainteDtos.PlainteSearchResult;
import com.pointage.plainte.PlainteDtos.PlainteSlice;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parses the uploaded "Plaintes &lt;Mois&gt; &lt;Année&gt;.xlsx" files and aggregates
 * counts per dimension (Priorité, type client, responsabilité, état ticket,
 * type produit, type produit × service impacté).
 *
 * <p>Headers are on row 0 of the first sheet:
 * 0 Statut, 1 N° ticket, 2 Date d'ouverture, 3 Raison sociale, 4 Type de Produit,
 * 5 Client Skills, 6 Service impacté, 7 Priorité, 8 Etat Ticket, 9 EDS Actif,
 * 10 Réparé par, 11 Responsabilité, 12 Commentaire.</p>
 */
@Service
public class PlainteService {

    public static final String DIM_PRIORITE = "PRIORITE";
    public static final String DIM_TYPE_CLIENT = "TYPE_CLIENT";
    public static final String DIM_RESPONSABILITE = "RESPONSABILITE";
    public static final String DIM_ETAT_TICKET = "ETAT_TICKET";
    public static final String DIM_TYPE_PRODUIT = "TYPE_PRODUIT";
    public static final String DIM_PRODUIT_SERVICE = "PRODUIT_SERVICE";
    public static final String DIM_REPARE_PAR = "REPARE_PAR";

    private static final int COL_RAISON_SOCIALE = 3;
    private static final int COL_DATE_OUVERTURE = 2;
    private static final int COL_PRODUIT = 4;
    private static final int COL_CLIENT_SKILLS = 5;
    private static final int COL_SERVICE = 6;
    private static final int COL_PRIORITE = 7;
    private static final int COL_ETAT = 8;
    private static final int COL_REPARE_PAR = 10;
    private static final int COL_RESPONSABILITE = 11;

    private static final int RECURRENT_MIN_COUNT = 2;
    private static final int RECURRENT_MONTHS = 2;

    private static final List<String> ALL_DIMS = List.of(
            DIM_PRIORITE, DIM_TYPE_CLIENT, DIM_RESPONSABILITE,
            DIM_ETAT_TICKET, DIM_TYPE_PRODUIT, DIM_PRODUIT_SERVICE,
            DIM_REPARE_PAR
    );

    private static final Map<String, Integer> MONTH_INDEX = Map.ofEntries(
            Map.entry("janvier", 1), Map.entry("fevrier", 2), Map.entry("mars", 3),
            Map.entry("avril", 4), Map.entry("mai", 5), Map.entry("juin", 6),
            Map.entry("juillet", 7), Map.entry("aout", 8), Map.entry("septembre", 9),
            Map.entry("octobre", 10), Map.entry("novembre", 11), Map.entry("decembre", 12),
            Map.entry("january", 1), Map.entry("february", 2), Map.entry("march", 3),
            Map.entry("april", 4), Map.entry("may", 5), Map.entry("june", 6),
            Map.entry("july", 7), Map.entry("august", 8), Map.entry("september", 9),
            Map.entry("october", 10), Map.entry("november", 11), Map.entry("december", 12)
    );

    private final DataFormatter formatter = new DataFormatter();

    private final Path plaintesDir;

    /** Cache: fileName -> parsed counts, invalidated when the file changes (upload). */
    private final Map<String, CachedFile> cache = new HashMap<>();

    private record CachedFile(long lastModified, Map<String, Map<String, Long>> counts,
                              List<ComplaintRow> rows) {
    }

    /** One meaningful row of a plaintes file, used for recurrent-complaint detection and search. */
    private record ComplaintRow(LocalDate dateOuverture, String ticketNumber, String statut,
                                String raisonSociale, String produit, String clientSkills,
                                String service, String priorite, String etat,
                                String reparePar, String responsabilite) {
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
    );

    public PlainteService(@Value("${app.upload-path:../uploads}") String uploadPath) {
        this.plaintesDir = Path.of(uploadPath, "plaintes");
    }

    public void clearCache() {
        cache.clear();
    }

    public Path getPlaintesDir() {
        return plaintesDir;
    }

    public PlainteOverview getOverview(String fileFilter) throws Exception {
        Map<String, CachedFile> byFile = loadAll();

        List<PlainteFileInfo> files = new ArrayList<>();
        for (String name : byFile.keySet()) {
            files.add(new PlainteFileInfo(name, labelOf(name)));
        }
        files.sort(Comparator.comparingInt(PlainteService::monthRank).reversed());

        Set<String> selected;
        if (fileFilter == null || fileFilter.isBlank() || "ALL".equalsIgnoreCase(fileFilter)) {
            selected = byFile.keySet();
        } else if (byFile.containsKey(fileFilter)) {
            selected = Set.of(fileFilter);
        } else {
            selected = Set.of();
        }

        Map<String, Map<String, Long>> merged = new LinkedHashMap<>();
        for (String dim : ALL_DIMS) {
            merged.put(dim, new HashMap<>());
        }
        for (String file : selected) {
            CachedFile cached = byFile.get(file);
            if (cached == null) {
                continue;
            }
            for (Map.Entry<String, Map<String, Long>> dimEntry : cached.counts().entrySet()) {
                Map<String, Long> target = merged.computeIfAbsent(dimEntry.getKey(), k -> new HashMap<>());
                for (Map.Entry<String, Long> e : dimEntry.getValue().entrySet()) {
                    target.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
        }

        Map<String, List<PlainteSlice>> stats = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> dimEntry : merged.entrySet()) {
            List<PlainteSlice> slices = dimEntry.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(e -> new PlainteSlice(e.getKey(), e.getValue()))
                    .toList();
            stats.put(dimEntry.getKey(), slices);
        }

        List<PlainteRecurrent> recurrent = recurrentClients(selected, byFile);

        return new PlainteOverview(files, stats, recurrent);
    }

    /**
     * Searches complaint rows by raison sociale (client name) or N° ticket.
     * The match is a case-insensitive substring, ignoring accents; when query
     * is blank, every row of the selected file(s) is returned.
     */
    public List<PlainteSearchResult> search(String fileFilter, String query) throws Exception {
        Map<String, CachedFile> byFile = loadAll();

        Set<String> selected;
        if (fileFilter == null || fileFilter.isBlank() || "ALL".equalsIgnoreCase(fileFilter)) {
            selected = byFile.keySet();
        } else if (byFile.containsKey(fileFilter)) {
            selected = Set.of(fileFilter);
        } else {
            selected = Set.of();
        }

        String q = query == null ? "" : fold(query.trim()).toLowerCase(Locale.ROOT);

        List<PlainteSearchResult> results = new ArrayList<>();
        for (String file : selected) {
            CachedFile cached = byFile.get(file);
            if (cached == null) {
                continue;
            }
            for (ComplaintRow row : cached.rows()) {
                String raison = fold(row.raisonSociale()).toLowerCase(Locale.ROOT);
                String ticket = row.ticketNumber().toLowerCase(Locale.ROOT);
                if (!q.isEmpty() && !raison.contains(q) && !ticket.contains(q)) {
                    continue;
                }
                results.add(new PlainteSearchResult(
                        row.statut(),
                        row.ticketNumber(),
                        row.dateOuverture() == null ? null : row.dateOuverture().toString(),
                        row.raisonSociale(),
                        row.produit(),
                        row.clientSkills(),
                        row.service(),
                        row.priorite(),
                        row.etat(),
                        row.reparePar(),
                        row.responsabilite()
                ));
            }
        }
        results.sort(Comparator.comparing(PlainteSearchResult::ticketNumber));
        return results;
    }

    /** Raison sociale present 2+ times in the last RECURRENT_MONTHS months of loaded data. */
    private List<PlainteRecurrent> recurrentClients(Set<String> selected, Map<String, CachedFile> byFile) {
        Map<String, List<ComplaintRow>> byClient = new LinkedHashMap<>();
        for (String file : selected) {
            CachedFile cached = byFile.get(file);
            if (cached == null) {
                continue;
            }
            for (ComplaintRow row : cached.rows()) {
                String key = fold(row.raisonSociale()).toLowerCase(Locale.ROOT);
                byClient.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusMonths(RECURRENT_MONTHS);

        List<PlainteRecurrent> recurrent = new ArrayList<>();
        for (Map.Entry<String, List<ComplaintRow>> e : byClient.entrySet()) {
            List<ComplaintRow> rows = e.getValue();
            long count = rows.stream()
                    .filter(r -> r.dateOuverture() != null && !r.dateOuverture().isBefore(cutoff))
                    .count();
            if (count < RECURRENT_MIN_COUNT) {
                continue;
            }
            LocalDate minDate = rows.stream()
                    .map(ComplaintRow::dateOuverture)
                    .filter(Objects::nonNull).filter(d -> !d.isBefore(cutoff))
                    .min(Comparator.naturalOrder()).orElse(null);
            LocalDate maxDate = rows.stream()
                    .map(ComplaintRow::dateOuverture)
                    .filter(Objects::nonNull).filter(d -> !d.isBefore(cutoff))
                    .max(Comparator.naturalOrder()).orElse(null);
            String display = rows.stream()
                    .filter(r -> !r.raisonSociale().isBlank())
                    .map(ComplaintRow::raisonSociale)
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .findFirst().orElse(e.getKey());
            recurrent.add(new PlainteRecurrent(
                    display,
                    count,
                    minDate == null ? "" : minDate.toString(),
                    maxDate == null ? "" : maxDate.toString()));
        }
        recurrent.sort(Comparator.comparingLong(PlainteRecurrent::count).reversed()
                .thenComparing(PlainteRecurrent::raisonSociale));
        return recurrent;
    }

private Map<String, CachedFile> loadAll() throws Exception {
    Map<String, CachedFile> result = new LinkedHashMap<>();
    File dir = plaintesDir.toFile();
    File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".xlsx"));
    if (files == null) {
        return result;
    }
    for (File f : files) {
        CachedFile cached = cache.get(f.getName());
        if (cached == null || cached.lastModified() != f.lastModified()) {
            var parsed = parse(f);
            cached = new CachedFile(f.lastModified(), parsed.counts(), parsed.rows());
            cache.put(f.getName(), cached);
        }
        result.put(f.getName(), cached);
    }
    return result;
}

    private record ParseResult(Map<String, Map<String, Long>> counts, List<ComplaintRow> rows) {
    }

    private ParseResult parse(File file) throws Exception {
        Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
        List<ComplaintRow> rows = new ArrayList<>();
        for (String dim : ALL_DIMS) {
            counts.put(dim, new HashMap<>());
        }
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                String raisonSociale = cellStr(row, COL_RAISON_SOCIALE);
                LocalDate dateOuverture = parseDate(row, COL_DATE_OUVERTURE);
                String ticketNumber = cellStr(row, 1);
                String statut = cellStr(row, 0);
                String produit = cellStr(row, COL_PRODUIT);
                String skills = cellStr(row, COL_CLIENT_SKILLS);
                String service = cellStr(row, COL_SERVICE);
                String priorite = cellStr(row, COL_PRIORITE);
                String etat = cellStr(row, COL_ETAT);
                String resp = cellStr(row, COL_RESPONSABILITE);
                String reparePar = cellStr(row, COL_REPARE_PAR);

                if (produit.isEmpty() && skills.isEmpty() && service.isEmpty()
                        && priorite.isEmpty() && etat.isEmpty() && resp.isEmpty() && reparePar.isEmpty()) {
                    continue;
                }

                add(counts, DIM_PRIORITE, priorite);
                add(counts, DIM_TYPE_CLIENT, skills);
                add(counts, DIM_RESPONSABILITE, resp);
                add(counts, DIM_ETAT_TICKET, etat);
                add(counts, DIM_TYPE_PRODUIT, produit);
                add(counts, DIM_REPARE_PAR, reparePar);
                if (!produit.isEmpty() && !service.isEmpty()) {
                    add(counts, DIM_PRODUIT_SERVICE, produit + " / " + service);
                }
                if (!raisonSociale.isEmpty() || !ticketNumber.isEmpty()) {
                    rows.add(new ComplaintRow(dateOuverture, ticketNumber, statut,
                            raisonSociale, produit, skills, service, priorite, etat, reparePar, resp));
                }
            }
        }
        return new ParseResult(counts, rows);
    }

    private LocalDate parseDate(Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
        } catch (Exception ignored) {
        }
        String text = cellStr(row, idx);
        if (text.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, fmt);
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private void add(Map<String, Map<String, Long>> counts, String dim, String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return;
        }
        counts.get(dim).merge(value, 1L, Long::sum);
    }

    private String cellStr(Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) {
            return "";
        }
        String value = formatter.formatCellValue(cell).strip();
        if ("-".equals(value) || "—".equals(value)) {
            return "";
        }
        return value;
    }

    /** "Plaintes Juillet 2026.xlsx" -> "Juillet 2026"; otherwise the stem is kept. */
    private String labelOf(String name) {
        String stem = name;
        if (stem.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            stem = stem.substring(0, stem.length() - 5);
        }
        String lowered = stem.trim().toLowerCase(Locale.ROOT);
        if (lowered.startsWith("plaintes")) {
            stem = stem.trim().substring(8).trim();
        }
        return stem.isEmpty() ? name : stem;
    }

    /** Higher rank = more recent month, so files sort newest-first. */
    private static int monthRank(PlainteFileInfo info) {
        String label = fold(info.label()).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : MONTH_INDEX.entrySet()) {
            if (label.contains(e.getKey())) {
                return extractYear(label) * 12 + e.getValue();
            }
        }
        return 0;
    }

    private static int extractYear(String label) {
        for (int i = label.length() - 4; i >= 0; i--) {
            String cand = label.substring(i, i + 4);
            if (cand.matches("20\\d\\d")) {
                return Integer.parseInt(cand);
            }
        }
        return 0;
    }

    private static String fold(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case 'à', 'â', 'ä' -> sb.append('a');
                case 'ç' -> sb.append('c');
                case 'é', 'è', 'ê', 'ë' -> sb.append('e');
                case 'î', 'ï' -> sb.append('i');
                case 'ô', 'ö' -> sb.append('o');
                case 'ù', 'û', 'ü' -> sb.append('u');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}