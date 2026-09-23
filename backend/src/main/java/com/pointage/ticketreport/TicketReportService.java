package com.pointage.ticketreport;

import com.pointage.user.OfficialTeam;
import com.pointage.user.User;
import com.pointage.user.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TicketReportService {

    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(14, 0);
    private static final LocalTime WORK_START = LocalTime.of(8, 0);
    private static final LocalTime LATE_THRESHOLD = LocalTime.of(9, 0);
    private static final long GAP_THRESHOLD_MINUTES = 120;

    /** Excel name spellings that differ from the official member name. */
    private static final Map<String, String> FULL_NAME_ALIASES = Map.of(
            "thouri nabil hafedh", "Nabil Thouri"
    );

    private final UserRepository userRepository;
    private final String uploadPath;

    /** Cache: fileName -> cached parsed tickets, invalidated when the file changes (upload). */
    private final Map<String, CachedFile> cache = new HashMap<>();

    private record CachedFile(long lastModified, List<TicketDto> tickets) {
    }

    public void clearCache() {
        cache.clear();
    }

    public TicketReportService(UserRepository userRepository,
                               @Value("${app.upload-path:../uploads}") String uploadPath) {
        this.userRepository = userRepository;
        this.uploadPath = uploadPath;
    }

    public TicketReportResponse getTicketReport(String team, LocalDate start, LocalDate end) {
        Map<String, String> excelToDbName = buildNameMap(team);

        List<TicketDto> tickets;
        if ("B2B".equals(team)) {
            tickets = parseB2BFiles(start, end, excelToDbName);
        } else {
            tickets = parseGPFile(start, end, excelToDbName);
        }

        List<TicketDto> deduped = deduplicateByTicketNumber(tickets);

        Map<String, List<TicketDto>> byUser = deduped.stream()
                .filter(t -> OfficialTeam.isOfficial(User.Team.valueOf(team), t.userName()))
                .collect(Collectors.groupingBy(TicketDto::userName));

        // Alerts follow the month filter on their own date dimension:
        // B2B -> created date, GP -> acquittement date.
        boolean useAcquittement = "GP".equals(team);
        Map<String, List<TicketDto>> alertsByUser = deduped.stream()
                .filter(t -> OfficialTeam.isOfficial(User.Team.valueOf(team), t.userName()))
                .filter(t -> alertInRange(t, useAcquittement, start, end))
                .collect(Collectors.groupingBy(TicketDto::userName));

        List<UserTicketStatsDto> stats = new ArrayList<>();
        for (Map.Entry<String, List<TicketDto>> entry : byUser.entrySet()) {
            stats.add(new UserTicketStatsDto(
                    entry.getKey(),
                    team,
                    entry.getValue().size(),
                    entry.getValue().stream()
                            .sorted(Comparator.comparing(TicketDto::resolutionDateTime).reversed())
                            .collect(Collectors.toList())
            ));
        }
        stats.sort(Comparator.comparing(UserTicketStatsDto::totalTickets).reversed());

        List<TicketAlert> alerts = generateAlerts(alertsByUser, useAcquittement, start, end);

        return new TicketReportResponse(stats, alerts);
    }

    private boolean alertInRange(TicketDto t, boolean useAcquittement, LocalDate start, LocalDate end) {
        String dateStr = useAcquittement ? t.acquittementDate() : t.createdDate();
        if (dateStr == null) dateStr = t.createdDate();
        if (dateStr == null) return true;
        return isInRange(LocalDate.parse(dateStr), start, end);
    }

    public TicketEvolutionDto getTicketEvolution(String team, LocalDate start, LocalDate end) {
        Map<String, String> excelToDbName = buildNameMap(team);

        List<TicketDto> tickets;
        if ("B2B".equals(team)) {
            tickets = parseB2BFiles(start, end, excelToDbName);
        } else {
            tickets = parseGPFile(start, end, excelToDbName);
        }

        List<TicketDto> deduped = deduplicateByTicketNumber(tickets).stream()
                .filter(t -> OfficialTeam.isOfficial(User.Team.valueOf(team), t.userName()))
                .toList();

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }

        List<Integer> totalPerDay = new ArrayList<>();
        Map<String, List<Integer>> perUser = new HashMap<>();
        Map<String, List<Integer>> bySource = new HashMap<>();

        for (TicketDto t : deduped) {
            perUser.computeIfAbsent(t.userName(), k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
            bySource.computeIfAbsent(cleanSource(t.source()), k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
        }

        for (int i = 0; i < dates.size(); i++) {
            LocalDate day = dates.get(i);
            int dayTotal = 0;
            for (TicketDto t : deduped) {
                if (t.resolutionDate() == null) continue;
                LocalDate res = LocalDate.parse(t.resolutionDate());
                if (res.equals(day)) {
                    dayTotal++;
                    perUser.get(t.userName()).set(i, perUser.get(t.userName()).get(i) + 1);
                    bySource.get(cleanSource(t.source())).set(i, bySource.get(cleanSource(t.source())).get(i) + 1);
                }
            }
            totalPerDay.add(dayTotal);
        }

        List<String> dateLabels = dates.stream()
                .map(d -> d.format(DateTimeFormatter.ofPattern("dd/MM")))
                .toList();

        return new TicketEvolutionDto(dateLabels, totalPerDay, perUser, bySource, Map.of());
    }

    public TicketEvolutionDto getTicketAcquittementEvolution(String team, LocalDate start, LocalDate end) {
        Map<String, String> excelToDbName = buildNameMap(team);

        // Acquisitions must be counted per acquittement row (a ticket appears on
        // several rows, one per champ). We therefore do NOT deduplicate by ticket.
        List<TicketDto> tickets;
        if ("B2B".equals(team)) {
            tickets = parseB2BAll(excelToDbName);
        } else {
            tickets = parseGPAll(excelToDbName);
        }

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }

        List<Integer> totalPerDay = new ArrayList<>(Collections.nCopies(dates.size(), 0));
        Map<String, List<Integer>> perUser = new HashMap<>();
        Map<String, List<Integer>> bySource = new HashMap<>();
        Map<String, Map<String, List<Integer>>> perUserBySource = new HashMap<>();
        Set<String> seen = new HashSet<>();

        for (TicketDto t : tickets) {
            String acqUser = acquittementUser(t);
            // Only official members as acquittement users.
            if (!OfficialTeam.isOfficial(User.Team.valueOf(team), acqUser)) continue;

            LocalDate acq = acquittementDate(t);
            if (acq == null) continue;
            int idx = dates.indexOf(acq);
            if (idx < 0) continue;

            // Each (ticket, acq user, day) counts once even when spread over several rows.
            if (!seen.add(t.ticketNumber() + "|" + acqUser + "|" + acq)) continue;

            String typeProduit = t.typeProduit() != null && !t.typeProduit().isBlank()
                    ? t.typeProduit().trim()
                    : null;

            totalPerDay.set(idx, totalPerDay.get(idx) + 1);
            perUser.computeIfAbsent(acqUser, k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
            perUser.get(acqUser).set(idx, perUser.get(acqUser).get(idx) + 1);

            // Type produit breakdown (GP TDB sheet only, otherwise empty).
            if (typeProduit != null) {
                bySource.computeIfAbsent(typeProduit, k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
                bySource.get(typeProduit).set(idx, bySource.get(typeProduit).get(idx) + 1);
                perUserBySource
                        .computeIfAbsent(typeProduit, k -> new HashMap<>())
                        .computeIfAbsent(acqUser, k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
                perUserBySource.get(typeProduit).get(acqUser).set(idx, perUserBySource.get(typeProduit).get(acqUser).get(idx) + 1);
            }
        }

        List<String> dateLabels = dates.stream()
                .map(d -> d.format(DateTimeFormatter.ofPattern("dd/MM")))
                .toList();

        return new TicketEvolutionDto(dateLabels, totalPerDay, perUser, bySource, perUserBySource);
    }

    private LocalDate acquittementDate(TicketDto t) {
        if (t.acquittementDate() != null) {
            return LocalDate.parse(t.acquittementDate());
        }
        // Fall back to the resolution date when no acquittement date exists.
        if (t.resolutionDate() != null) {
            return LocalDate.parse(t.resolutionDate());
        }
        return null;
    }

    private String acquittementUser(TicketDto t) {
        if (t.acquittementUser() != null && !t.acquittementUser().isBlank()) {
            return t.acquittementUser();
        }
        return t.userName();
    }

    public TicketEvolutionDto getTicketCreatedEvolution(String team, LocalDate start, LocalDate end) {
        Map<String, String> excelToDbName = buildNameMap(team);

        // Creation is counted per (ticket, created user, day), like acquisitions.
        List<TicketDto> tickets;
        if ("B2B".equals(team)) {
            tickets = parseB2BAll(excelToDbName);
        } else {
            tickets = parseGPAll(excelToDbName);
        }

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }

        List<Integer> totalPerDay = new ArrayList<>(Collections.nCopies(dates.size(), 0));
        Map<String, List<Integer>> perUser = new HashMap<>();
        Map<String, List<Integer>> bySource = new HashMap<>();
        Map<String, Map<String, List<Integer>>> perUserBySource = new HashMap<>();
        Set<String> seen = new HashSet<>();

        for (TicketDto t : tickets) {
            String createdUser = createdUser(t);
            // Only official members as creation users.
            if (!OfficialTeam.isOfficial(User.Team.valueOf(team), createdUser)) continue;

            LocalDate created = createdDate(t);
            if (created == null) continue;
            int idx = dates.indexOf(created);
            if (idx < 0) continue;

            // Each (ticket, created user, day) counts once even when spread over several rows.
            if (!seen.add(t.ticketNumber() + "|" + createdUser + "|" + created)) continue;

            String source = cleanSource(t.source());
            totalPerDay.set(idx, totalPerDay.get(idx) + 1);
            perUser.computeIfAbsent(createdUser, k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
            perUser.get(createdUser).set(idx, perUser.get(createdUser).get(idx) + 1);
            bySource.computeIfAbsent(source, k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
            bySource.get(source).set(idx, bySource.get(source).get(idx) + 1);
            perUserBySource
                    .computeIfAbsent(source, k -> new HashMap<>())
                    .computeIfAbsent(createdUser, k -> new ArrayList<>(Collections.nCopies(dates.size(), 0)));
            perUserBySource.get(source).get(createdUser).set(idx, perUserBySource.get(source).get(createdUser).get(idx) + 1);
        }

        List<String> dateLabels = dates.stream()
                .map(d -> d.format(DateTimeFormatter.ofPattern("dd/MM")))
                .toList();

        return new TicketEvolutionDto(dateLabels, totalPerDay, perUser, bySource, perUserBySource);
    }

    private LocalDate createdDate(TicketDto t) {
        if (t.createdDate() != null) {
            return LocalDate.parse(t.createdDate());
        }
        if (t.resolutionDate() != null) {
            return LocalDate.parse(t.resolutionDate());
        }
        return null;
    }

    private String createdUser(TicketDto t) {
        if (t.createdUser() != null && !t.createdUser().isBlank()) {
            return t.createdUser();
        }
        return t.userName();
    }

    private String cleanSource(String source) {
        if (source == null) return "Autre";
        if (source.contains("ATP")) return "ATP";
        if (source.contains("TDB")) return "GP";
        if (source.contains("SMC")) return "SMC_BO";
        return source;
    }

    private Map<String, String> buildNameMap(String team) {
        List<User> teamUsers = userRepository.findAll().stream()
                .filter(u -> u.getTeam().name().equals(team))
                .collect(Collectors.toList());

        Map<String, String> excelToDbName = new HashMap<>();
        for (User u : teamUsers) {
            String[] dbWords = normalizeName(u.getFullName()).split("\\s+");
            excelToDbName.put(String.join(" ", dbWords), u.getFullName());
        }
        return excelToDbName;
    }

    public Map<String, Integer> getUserTicketCountsByFullName(LocalDate start, LocalDate end) {
        Map<String, Integer> counts = new HashMap<>();
        for (User.Team team : User.Team.values()) {
            List<User> teamUsers = userRepository.findAll().stream()
                    .filter(u -> u.getTeam() == team && OfficialTeam.isOfficial(team, u.getFullName()))
                    .collect(Collectors.toList());

            Map<String, String> excelToDbName = new HashMap<>();
            for (User u : teamUsers) {
                String[] dbWords = normalizeName(u.getFullName()).split("\\s+");
                excelToDbName.put(String.join(" ", dbWords), u.getFullName());
            }

            List<TicketDto> tickets = ("B2B".equals(team.name()))
                    ? parseB2BFiles(start, end, excelToDbName)
                    : parseGPFile(start, end, excelToDbName);

            for (TicketDto t : deduplicateByTicketNumber(tickets)) {
                counts.merge(t.userName(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Returns, per official user, the set of dates on which the user resolved
     * at least one ticket. Used to decide a booked day's WORKED/MISSED status
     * automatically from the Excel reports (no manual report needed).
     */
    public Map<String, Set<LocalDate>> getUserResolvedDays(LocalDate start, LocalDate end) {
        Map<String, Set<LocalDate>> resolved = new HashMap<>();
        for (User.Team team : User.Team.values()) {
            Map<String, String> excelToDbName = buildNameMap(team.name());
            List<TicketDto> tickets = ("B2B".equals(team.name()))
                    ? parseB2BFiles(start, end, excelToDbName)
                    : parseGPFile(start, end, excelToDbName);

            for (TicketDto t : deduplicateByTicketNumber(tickets)) {
                if (t.resolutionDate() == null) continue;
                if (!OfficialTeam.isOfficial(team, t.userName())) continue;
                resolved.computeIfAbsent(t.userName(), k -> new HashSet<>())
                        .add(LocalDate.parse(t.resolutionDate()));
            }
        }
        return resolved;
    }

    public Set<LocalDate> getResolvedDaysForUser(String team, String fullName, LocalDate start, LocalDate end) {
        Set<LocalDate> days = new HashSet<>();
        Map<String, String> excelToDbName = buildNameMap(team);
        List<TicketDto> tickets = ("B2B".equals(team))
                ? parseB2BFiles(start, end, excelToDbName)
                : parseGPFile(start, end, excelToDbName);
        for (TicketDto t : deduplicateByTicketNumber(tickets)) {
            if (t.resolutionDate() == null) continue;
            if (!normalizeName(t.userName()).equals(normalizeName(fullName))) continue;
            days.add(LocalDate.parse(t.resolutionDate()));
        }
        return days;
    }

    private List<TicketAlert> generateAlerts(Map<String, List<TicketDto>> byUser, boolean useAcquittement,
                                         LocalDate start, LocalDate end) {
        List<TicketAlert> alerts = new ArrayList<>();

        for (Map.Entry<String, List<TicketDto>> entry : byUser.entrySet()) {
            String userName = entry.getKey();
            List<TicketDto> userTickets = entry.getValue().stream()
                    .filter(t -> activityDateTime(t, useAcquittement) != null)
                    .sorted(Comparator.comparing(t -> activityDateTime(t, useAcquittement)))
                    .collect(Collectors.toList());

            if (userTickets.isEmpty()) continue;

            Map<LocalDate, List<TicketDto>> byDay = userTickets.stream()
                    .collect(Collectors.groupingBy(t -> activityDateTime(t, useAcquittement).toLocalDate()));

            for (Map.Entry<LocalDate, List<TicketDto>> dayEntry : byDay.entrySet()) {
                LocalDate day = dayEntry.getKey();

                // Alerts belong to the selected period (month filter).
                if (day.isBefore(start) || day.isAfter(end)) continue;

                List<TicketDto> dayTickets = dayEntry.getValue();

                TicketDto first = dayTickets.get(0);
                LocalTime firstTime = activityDateTime(first, useAcquittement).toLocalTime();
                if (firstTime.isAfter(LATE_THRESHOLD) || firstTime.equals(LATE_THRESHOLD)) {
                    alerts.add(new TicketAlert(
                            "LATE_START",
                            userName,
                            "Le " + day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                                    " — ticket " + first.ticketNumber() +
                                    (useAcquittement ? " acquitté à " : " ouvert à ") +
                                    firstTime.format(DateTimeFormatter.ofPattern("HH:mm")) +
                                    " (après 9h00)",
                            first.createdDateTime() != null ? first.createdDateTime()
                                    : activityDateTime(first, useAcquittement).toString()
                    ));
                }

                for (int i = 0; i < dayTickets.size() - 1; i++) {
                    TicketDto current = dayTickets.get(i);
                    TicketDto next = dayTickets.get(i + 1);

                    LocalDateTime endTime = activityDateTime(current, useAcquittement);
                    LocalDateTime nextStart = activityDateTime(next, useAcquittement);

                    long gapMinutes = calculateWorkGapMinutes(endTime, nextStart);

                    if (gapMinutes >= GAP_THRESHOLD_MINUTES) {
                        long hours = gapMinutes / 60;
                        long mins = gapMinutes % 60;
                        String gapStr = hours > 0 ? hours + "h" + (mins > 0 ? mins + "min" : "") : mins + "min";
                        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                        alerts.add(new TicketAlert(
                                "LONG_GAP",
                                userName,
                                "Pause de " + gapStr +
                                        " le " + day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                                        " — ticket " + current.ticketNumber() +
                                        " (" + endTime.format(dtf) + ") → ticket " +
                                        next.ticketNumber() + " (" + nextStart.format(dtf) + ")",
                                current.createdDateTime() != null ? current.createdDateTime()
                                        : activityDateTime(current, useAcquittement).toString()
                        ));
                    }
                }
            }
        }

        alerts.sort(Comparator.comparing(TicketAlert::timestamp));
        return alerts;
    }

    private LocalDateTime activityDateTime(TicketDto t, boolean useAcquittement) {
        if (useAcquittement && t.acquittementDateTime() != null) {
            return LocalDateTime.parse(t.acquittementDateTime());
        }
        if (t.createdDateTime() != null) {
            return LocalDateTime.parse(t.createdDateTime());
        }
        return null;
    }

    private long calculateWorkGapMinutes(LocalDateTime end, LocalDateTime start) {
        if (!start.isAfter(end)) return 0;
        if (!end.toLocalDate().equals(start.toLocalDate())) return 0;

        long totalMinutes = Duration.between(end, start).toMinutes();

        LocalDateTime lunchStartDt = end.toLocalDate().atTime(LUNCH_START);
        LocalDateTime lunchEndDt = end.toLocalDate().atTime(LUNCH_END);

        LocalDateTime overlapStart = end.isAfter(lunchStartDt) ? end : lunchStartDt;
        LocalDateTime overlapEnd = start.isBefore(lunchEndDt) ? start : lunchEndDt;

        if (overlapEnd.isAfter(overlapStart)) {
            totalMinutes -= Duration.between(overlapStart, overlapEnd).toMinutes();
        }

        return Math.max(0, totalMinutes);
    }

    // --- Excel parsing methods ---

    private List<TicketDto> parseB2BFiles(LocalDate start, LocalDate end,
                                          Map<String, String> excelToDbName) {
        List<TicketDto> all = new ArrayList<>();
        all.addAll(parseRapportSMCBO(start, end, excelToDbName));
        all.addAll(parseRapportATP(start, end, excelToDbName));
        return all;
    }

    private List<TicketDto> parseB2BAll(Map<String, String> excelToDbName) {
        List<TicketDto> all = new ArrayList<>();
        all.addAll(parseSMCBOAll(excelToDbName));
        all.addAll(parseATPAll(excelToDbName));
        return all;
    }

    private List<TicketDto> parseSMCBOAll(Map<String, String> excelToDbName) {
        return parseFile("Rapport_SMC_BO.xlsx|all", "Rapport_SMC_BO.xlsx", excelToDbName, this::parseSMCBORowsAll, 4);
    }

    private List<TicketDto> parseATPAll(Map<String, String> excelToDbName) {
        return parseFile("Rapport ATP.xlsx|all", "Rapport ATP_WO_355_4225286481794306943.xlsx", excelToDbName, this::parseATPRowsAll, 3);
    }

    private List<TicketDto> parseGPAll(Map<String, String> excelToDbName) {
        return parseFile("TDBRapport_SMC_BO.xlsx|all", "TDBRapport_SMC_BO.xlsx", excelToDbName, this::parseGPRowsAll, 4);
    }

    private interface RowParser {
        List<TicketDto> parse(Workbook wb, Map<String, String> excelToDbName, int headerSkip);
    }

    private List<TicketDto> parseFile(String cacheKey, String fileName, Map<String, String> excelToDbName,
                                      RowParser rowParser, int headerSkip) {
        File file = new File(uploadPath, fileName);
        if (!file.exists()) return List.of();

        try {
            long modified = Files.getLastModifiedTime(file.toPath()).toMillis();
            CachedFile cached = cache.get(cacheKey);
            if (cached != null && cached.lastModified() == modified) {
                return cached.tickets();
            }

            List<TicketDto> parsed;
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(Files.readAllBytes(file.toPath())))) {
                parsed = rowParser.parse(wb, excelToDbName, headerSkip);
            }
            cache.put(cacheKey, new CachedFile(modified, parsed));
            return parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<TicketDto> filterByRange(List<TicketDto> tickets, LocalDate start, LocalDate end) {
        return tickets.stream()
                .filter(t -> {
                    // Filter by resolution date when present, else by creation date.
                    String dateStr = t.resolutionDate();
                    if (dateStr == null) dateStr = t.createdDate();
                    if (dateStr == null) return true;
                    LocalDate d = LocalDate.parse(dateStr);
                    return isInRange(d, start, end);
                })
                .toList();
    }

    private List<TicketDto> parseRapportSMCBO(LocalDate start, LocalDate end,
                                              Map<String, String> excelToDbName) {
        List<TicketDto> all = parseFile("Rapport_SMC_BO.xlsx|req", "Rapport_SMC_BO.xlsx", excelToDbName, this::parseSMCBORows, 4);
        return filterByRange(all, start, end);
    }

    private List<TicketDto> parseSMCBORows(Workbook wb, Map<String, String> excelToDbName, int headerSkip) {
        return parseSMCBORows(wb, excelToDbName, headerSkip, true);
    }

    private List<TicketDto> parseSMCBORowsAll(Workbook wb, Map<String, String> excelToDbName, int headerSkip) {
        return parseSMCBORows(wb, excelToDbName, headerSkip, false);
    }

    private List<TicketDto> parseSMCBORows(Workbook wb, Map<String, String> excelToDbName, int headerSkip, boolean requireRepair) {
        List<TicketDto> tickets = new ArrayList<>();
        Sheet sheet = wb.getSheetAt(0);
        for (Row row : sheet) {
            if (row.getRowNum() < headerSkip) continue;

            String ticketNumber = getStringCell(row, 1);
            if (ticketNumber == null || ticketNumber.isBlank()) continue;

            LocalDateTime repairDt = getDateTimeCell(row, 14);
            LocalDateTime createdDt = getDateTimeCell(row, 2);
            LocalDate followUpDate = getDateCell(row, 15);
            LocalDate acquittementDate = getDateCell(row, 4);

            String rawName = getStringCell(row, 21);
            if (rawName == null || rawName.isBlank()) rawName = getStringCell(row, 19);
            if (rawName == null || rawName.isBlank()) rawName = getStringCell(row, 13);

            String rawAcqUser = getStringCell(row, 17);
            if (rawAcqUser == null || rawAcqUser.isBlank()) rawAcqUser = rawName;

            if (requireRepair && repairDt == null) continue;
            if (rawName == null || rawName.isBlank()) continue;

            String matchedName = matchName(rawName, excelToDbName);
            String acqUser = matchName(rawAcqUser, excelToDbName);
            String createdUser = matchName(getStringCell(row, 13), excelToDbName);
            if (createdUser == null || createdUser.isBlank()) createdUser = matchedName;

            tickets.add(new TicketDto(
                    ticketNumber,
                    matchedName,
                    createdDt != null ? createdDt.toLocalDate().toString() : null,
                    repairDt != null ? repairDt.toLocalDate().toString() : null,
                    createdDt != null ? createdDt.toString() : null,
                    repairDt != null ? repairDt.toString() : null,
                    followUpDate != null ? followUpDate.toString() : null,
                    "Rapport_SMC_BO",
                    acquittementDate != null ? acquittementDate.toString() : null,
                    acqUser,
                    createdUser,
                    null,
                    null
            ));
        }
        return tickets;
    }

    private List<TicketDto> parseRapportATP(LocalDate start, LocalDate end,
                                            Map<String, String> excelToDbName) {
        List<TicketDto> all = parseFile("Rapport ATP.xlsx|req", "Rapport ATP_WO_355_4225286481794306943.xlsx", excelToDbName, this::parseATPRows, 3);
        return filterByRange(all, start, end);
    }

    private List<TicketDto> parseATPRows(Workbook wb, Map<String, String> excelToDbName, int headerSkip) {
        return parseATPRows(wb, excelToDbName, headerSkip, true);
    }

    private List<TicketDto> parseATPRowsAll(Workbook wb, Map<String, String> excelToDbName, int headerSkip) {
        return parseATPRows(wb, excelToDbName, headerSkip, false);
    }

    private List<TicketDto> parseATPRows(Workbook wb, Map<String, String> excelToDbName, int headerSkip, boolean requireEnd) {
        List<TicketDto> tickets = new ArrayList<>();
        Sheet sheet = wb.getSheetAt(0);
        for (Row row : sheet) {
            if (row.getRowNum() < headerSkip) continue;

            String ticketNumber = getStringCell(row, 2);
            if (ticketNumber == null || ticketNumber.isBlank()) continue;

            LocalDateTime createdDt = getDateTimeCell(row, 0);
            LocalDateTime endDt = getDateTimeCell(row, 3);
            String rawName = getStringCell(row, 7);

            if (requireEnd && endDt == null) continue;
            if (rawName == null || rawName.isBlank()) continue;

            String matchedName = matchName(rawName, excelToDbName);

            tickets.add(new TicketDto(
                    ticketNumber,
                    matchedName,
                    createdDt != null ? createdDt.toLocalDate().toString() : null,
                    endDt != null ? endDt.toLocalDate().toString() : null,
                    createdDt != null ? createdDt.toString() : null,
                    endDt != null ? endDt.toString() : null,
                    null,
                    "Rapport_ATP_WO",
                    endDt != null ? endDt.toLocalDate().toString() : null,
                    matchedName,
                    matchedName,
                    null,
                    null
            ));
        }
        return tickets;
    }

    private List<TicketDto> parseGPFile(LocalDate start, LocalDate end,
                                        Map<String, String> excelToDbName) {
        List<TicketDto> all = parseFile("TDBRapport_SMC_BO.xlsx|req", "TDBRapport_SMC_BO.xlsx", excelToDbName, this::parseGPRows, 4);
        return filterByRange(all, start, end);
    }

    private List<TicketDto> parseGPRows(Workbook wb, Map<String, String> excelToDbName, int headerSkip) {
        return parseGPRows(wb, excelToDbName, headerSkip, true);
    }

    private List<TicketDto> parseGPRowsAll(Workbook wb, Map<String, String> excelToDbName, int headerSkip) {
        return parseGPRows(wb, excelToDbName, headerSkip, false);
    }

    private List<TicketDto> parseGPRows(Workbook wb, Map<String, String> excelToDbName, int headerSkip, boolean requireRepair) {
        List<TicketDto> tickets = new ArrayList<>();
        Sheet sheet = wb.getSheetAt(0);
        for (Row row : sheet) {
            if (row.getRowNum() < headerSkip) continue;

            String ticketNumber = getStringCell(row, 1);
            if (ticketNumber == null || ticketNumber.isBlank()) continue;

            LocalDateTime repairDt = getDateTimeCell(row, 15);
            LocalDateTime createdDt = getDateTimeCell(row, 2);
            LocalDate followUpDate = getDateCell(row, 11);
            LocalDate acquittementDate = getDateCell(row, 4);
            String rawName = getStringCell(row, 5);
            String typeProduit = getStringCell(row, 9);
            LocalDateTime acquittementDt = getDateTimeCell(row, 4);

            if (requireRepair && repairDt == null) continue;
            if (rawName == null || rawName.isBlank()) continue;

            String matchedName = matchName(rawName, excelToDbName);

            tickets.add(new TicketDto(
                    ticketNumber,
                    matchedName,
                    createdDt != null ? createdDt.toLocalDate().toString() : null,
                    repairDt != null ? repairDt.toLocalDate().toString() : null,
                    createdDt != null ? createdDt.toString() : null,
                    repairDt != null ? repairDt.toString() : null,
                    followUpDate != null ? followUpDate.toString() : null,
                    "TDBRapport_SMC_BO",
                    acquittementDate != null ? acquittementDate.toString() : null,
                    matchedName,
                    matchedName,
                    typeProduit,
                    acquittementDt != null ? acquittementDt.toString() : null
            ));
        }
        return tickets;
    }

    private List<TicketDto> deduplicateByTicketNumber(List<TicketDto> tickets) {
        Map<String, TicketDto> best = new LinkedHashMap<>();
        for (TicketDto t : tickets) {
            TicketDto existing = best.get(t.ticketNumber());
            if (existing == null) {
                best.put(t.ticketNumber(), t);
            } else if (t.resolutionDateTime() != null &&
                    t.resolutionDateTime().compareTo(existing.resolutionDateTime()) >= 0) {
                best.put(t.ticketNumber(), t);
            }
        }
        return new ArrayList<>(best.values());
    }

    private String matchName(String rawName, Map<String, String> excelToDbName) {
        // Some Excel spellings differ from the official member name.
        String normalized = normalizeName(rawName);
        for (Map.Entry<String, String> alias : FULL_NAME_ALIASES.entrySet()) {
            if (normalized.contains(normalizeName(alias.getKey())) || normalizeName(alias.getKey()).contains(normalized)) {
                String official = excelToDbName.get(normalizeName(alias.getValue()));
                return official != null ? official : alias.getValue();
            }
        }

        List<String> rawTokens = matchTokens(rawName);
        for (Map.Entry<String, String> entry : excelToDbName.entrySet()) {
            List<String> dbTokens = matchTokens(entry.getKey());
            if (rawTokens.equals(dbTokens)) return entry.getValue();

            Set<String> rawSet = new HashSet<>(rawTokens);
            Set<String> dbSet = new HashSet<>(dbTokens);
            rawSet.retainAll(dbSet);
            int common = rawSet.size();
            int minLen = Math.min(rawTokens.size(), dbTokens.size());
            if (minLen > 0 && common >= minLen) return entry.getValue();
        }
        return rawName;
    }

    private List<String> matchTokens(String name) {
        return Arrays.stream(normalizeName(name).split("\\s+"))
                .map(this::aliasNormToken)
                .collect(Collectors.toList());
    }

    private String aliasNormToken(String token) {
        // "Med" is a short form of "Mohamed" (e.g. Med Ali Essifi = Mohamed Ali Essifi).
        if ("med".equals(token)) return "mohamed";
        return token;
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String getStringCell(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String val;
        switch (cell.getCellType()) {
            case STRING:
                val = cell.getStringCellValue();
                break;
            case NUMERIC:
                val = String.valueOf((long) cell.getNumericCellValue());
                break;
            case BOOLEAN:
                val = String.valueOf(cell.getBooleanCellValue());
                break;
            default:
                val = null;
        }
        return (val != null && !val.isBlank()) ? val.trim() : null;
    }

    private LocalDateTime getDateTimeCell(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }

    private LocalDate getDateCell(Row row, int col) {
        LocalDateTime dt = getDateTimeCell(row, col);
        return dt != null ? dt.toLocalDate() : null;
    }

    private boolean isInRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
