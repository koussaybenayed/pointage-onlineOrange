package com.pointage.ticketreport;

import java.util.List;
import java.util.Map;

/**
 * Daily ticket evolution for the "rendement" curves.
 * dates           -> the days of the period (labels).
 * totalPerDay     -> number of tickets per day (masse).
 * perUser         -> userName -> tickets per day (unitaire).
 * bySource        -> source (SMC_BO / ATP...) -> tickets per day.
 * perUserBySource -> source -> userName -> tickets per day.
 */
public record TicketEvolutionDto(
        List<String> dates,
        List<Integer> totalPerDay,
        Map<String, List<Integer>> perUser,
        Map<String, List<Integer>> bySource,
        Map<String, Map<String, List<Integer>>> perUserBySource
) {
}