package com.pointage.ticketreport;

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

    private final UserRepository userRepository;
    private final String uploadPath;

    public TicketReportService(UserRepository userRepository,
                               @Value("${app.upload-path:../uploads}") String uploadPath) {
        this.userRepository = userRepository;
        this.uploadPath = uploadPath;
    }

    public TicketReportResponse getTicketReport(String team, LocalDate start, LocalDate end) {
        List<User> teamUsers = userRepository.findAll().stream()
                .filter(u -> u.getTeam().name().equals(team))
                .collect(Collectors.toList());

        Map<String, String> excelToDbName = new HashMap<>();
        for (User u : teamUsers) {
            String[] dbWords = normalizeName(u.getFullName()).split("\\s+");
            excelToDbName.put(String.join(" ", dbWords), u.getFullName());
        }

        List<TicketDto> tickets;
        if ("B2B".equals(team)) {
            tickets = parseB2BFiles(start, end, excelToDbName);
        } else {
            tickets = parseGPFile(start, end, excelToDbName);
        }

        List<TicketDto> deduped = deduplicateByTicketNumber(tickets);

        Map<String, List<TicketDto>> byUser = deduped.stream()
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

        List<TicketAlert> alerts = generateAlerts(byUser);

        return new TicketReportResponse(stats, alerts);
    }

    public Map<String, Integer> getUserTicketCountsByFullName(LocalDate start, LocalDate end) {
        Map<String, Integer> counts = new HashMap<>();
        for (User.Team team : User.Team.values()) {
            List<User> teamUsers = userRepository.findAll().stream()
                    .filter(u -> u.getTeam() == team)
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

    private List<TicketAlert> generateAlerts(Map<String, List<TicketDto>> byUser) {
        List<TicketAlert> alerts = new ArrayList<>();

        for (Map.Entry<String, List<TicketDto>> entry : byUser.entrySet()) {
            String userName = entry.getKey();
            List<TicketDto> userTickets = entry.getValue().stream()
                    .filter(t -> t.createdDateTime() != null)
                    .sorted(Comparator.comparing(TicketDto::createdDateTime))
                    .collect(Collectors.toList());

            if (userTickets.isEmpty()) continue;

            Map<LocalDate, List<TicketDto>> byDay = userTickets.stream()
                    .collect(Collectors.groupingBy(t -> LocalDateTime.parse(t.createdDateTime()).toLocalDate()));

            for (Map.Entry<LocalDate, List<TicketDto>> dayEntry : byDay.entrySet()) {
                LocalDate day = dayEntry.getKey();
                List<TicketDto> dayTickets = dayEntry.getValue();

                TicketDto first = dayTickets.get(0);
                LocalTime firstTime = LocalDateTime.parse(first.createdDateTime()).toLocalTime();
                if (firstTime.isAfter(LATE_THRESHOLD) || firstTime.equals(LATE_THRESHOLD)) {
                    alerts.add(new TicketAlert(
                            "LATE_START",
                            userName,
                            "Pas de ticket ouvert avant 9h00 (premier ticket à " + firstTime.format(DateTimeFormatter.ofPattern("HH:mm")) + ")",
                            first.createdDateTime()
                    ));
                }

                for (int i = 0; i < dayTickets.size() - 1; i++) {
                    TicketDto current = dayTickets.get(i);
                    TicketDto next = dayTickets.get(i + 1);

                    LocalDateTime endTime = LocalDateTime.parse(current.resolutionDateTime());
                    LocalDateTime nextStart = LocalDateTime.parse(next.createdDateTime());

                    long gapMinutes = calculateWorkGapMinutes(endTime, nextStart);

                    if (gapMinutes >= GAP_THRESHOLD_MINUTES) {
                        long hours = gapMinutes / 60;
                        long mins = gapMinutes % 60;
                        String gapStr = hours > 0 ? hours + "h" + (mins > 0 ? mins + "min" : "") : mins + "min";
                        alerts.add(new TicketAlert(
                                "LONG_GAP",
                                userName,
                                "Pause de " + gapStr + " entre " + current.ticketNumber() + " et " + next.ticketNumber(),
                                current.resolutionDateTime()
                        ));
                    }
                }
            }
        }

        alerts.sort(Comparator.comparing(TicketAlert::timestamp));
        return alerts;
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

    private List<TicketDto> parseRapportSMCBO(LocalDate start, LocalDate end,
                                              Map<String, String> excelToDbName) {
        List<TicketDto> tickets = new ArrayList<>();
        File file = new File(uploadPath, "Rapport_SMC_BO.xlsx");
        if (!file.exists()) return tickets;

        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(Files.readAllBytes(file.toPath())))) {

            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() < 4) continue;

                String ticketNumber = getStringCell(row, 1);
                if (ticketNumber == null || ticketNumber.isBlank()) continue;

                LocalDateTime repairDt = getDateTimeCell(row, 14);
                LocalDateTime createdDt = getDateTimeCell(row, 2);
                LocalDate followUpDate = getDateCell(row, 15);

                String rawName = getStringCell(row, 21);
                if (rawName == null || rawName.isBlank()) rawName = getStringCell(row, 19);
                if (rawName == null || rawName.isBlank()) rawName = getStringCell(row, 13);

                if (repairDt == null) continue;
                if (rawName == null || rawName.isBlank()) continue;
                if (!isInRange(repairDt.toLocalDate(), start, end)) continue;

                String matchedName = matchName(rawName, excelToDbName);

                tickets.add(new TicketDto(
                        ticketNumber,
                        matchedName,
                        createdDt != null ? createdDt.toLocalDate().toString() : null,
                        repairDt.toLocalDate().toString(),
                        createdDt != null ? createdDt.toString() : null,
                        repairDt.toString(),
                        followUpDate != null ? followUpDate.toString() : null,
                        "Rapport_SMC_BO"
                ));
            }
        } catch (Exception e) {
            // silently skip
        }
        return tickets;
    }

    private List<TicketDto> parseRapportATP(LocalDate start, LocalDate end,
                                            Map<String, String> excelToDbName) {
        List<TicketDto> tickets = new ArrayList<>();
        File file = new File(uploadPath, "Rapport ATP_WO_355_4225286481794306943.xlsx");
        if (!file.exists()) return tickets;

        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(Files.readAllBytes(file.toPath())))) {

            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() < 3) continue;

                String ticketNumber = getStringCell(row, 2);
                if (ticketNumber == null || ticketNumber.isBlank()) continue;

                LocalDateTime createdDt = getDateTimeCell(row, 0);
                LocalDateTime endDt = getDateTimeCell(row, 3);
                String rawName = getStringCell(row, 7);

                if (endDt == null) continue;
                if (rawName == null || rawName.isBlank()) continue;
                if (!isInRange(endDt.toLocalDate(), start, end)) continue;

                String matchedName = matchName(rawName, excelToDbName);

                tickets.add(new TicketDto(
                        ticketNumber,
                        matchedName,
                        createdDt != null ? createdDt.toLocalDate().toString() : null,
                        endDt.toLocalDate().toString(),
                        createdDt != null ? createdDt.toString() : null,
                        endDt.toString(),
                        null,
                        "Rapport_ATP_WO"
                ));
            }
        } catch (Exception e) {
            // silently skip
        }
        return tickets;
    }

    private List<TicketDto> parseGPFile(LocalDate start, LocalDate end,
                                        Map<String, String> excelToDbName) {
        List<TicketDto> tickets = new ArrayList<>();
        File file = new File(uploadPath, "TDBRapport_SMC_BO.xlsx");
        if (!file.exists()) return tickets;

        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(Files.readAllBytes(file.toPath())))) {

            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() < 4) continue;

                String ticketNumber = getStringCell(row, 1);
                if (ticketNumber == null || ticketNumber.isBlank()) continue;

                LocalDateTime repairDt = getDateTimeCell(row, 15);
                LocalDateTime createdDt = getDateTimeCell(row, 2);
                LocalDate followUpDate = getDateCell(row, 11);
                String rawName = getStringCell(row, 5);

                if (repairDt == null) continue;
                if (rawName == null || rawName.isBlank()) continue;
                if (!isInRange(repairDt.toLocalDate(), start, end)) continue;

                String matchedName = matchName(rawName, excelToDbName);

                tickets.add(new TicketDto(
                        ticketNumber,
                        matchedName,
                        createdDt != null ? createdDt.toLocalDate().toString() : null,
                        repairDt.toLocalDate().toString(),
                        createdDt != null ? createdDt.toString() : null,
                        repairDt.toString(),
                        followUpDate != null ? followUpDate.toString() : null,
                        "TDBRapport_SMC_BO"
                ));
            }
        } catch (Exception e) {
            // silently skip
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
        String normalized = normalizeName(rawName);
        for (Map.Entry<String, String> entry : excelToDbName.entrySet()) {
            String dbKey = entry.getKey();
            String dbFullName = entry.getValue();
            if (normalized.equals(dbKey)) return dbFullName;

            String[] rawWords = normalized.split("\\s+");
            String[] dbWords = dbKey.split("\\s+");
            Set<String> rawSet = new HashSet<>(Arrays.asList(rawWords));
            Set<String> dbSet = new HashSet<>(Arrays.asList(dbWords));
            rawSet.retainAll(dbSet);
            int common = rawSet.size();
            int minLen = Math.min(rawWords.length, dbWords.length);
            if (minLen > 0 && common >= minLen) return dbFullName;
        }
        return rawName;
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
