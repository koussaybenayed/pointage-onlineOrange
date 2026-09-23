package com.pointage.plainte;

import java.util.List;
import java.util.Map;

/**
 * DTOs for the "Plaintes" (complaints) module: pie-chart data per dimension.
 */
public final class PlainteDtos {

    private PlainteDtos() {
    }

    /** All uploaded plaintes files, aggregated counts per dimension and recurrent-complaint alerts. */
    public record PlainteOverview(List<PlainteFileInfo> files,
                                  Map<String, List<PlainteSlice>> stats,
                                  List<PlainteRecurrent> recurrent) {
    }

    public record PlainteFileInfo(String name, String label) {
    }

    public record PlainteSlice(String label, long count) {
    }

    /**
     * A client ("Raison sociale") that appears 2 times or more as complainant
     * in the last RECURRENT_MONTHS (2) months of the loaded data.
     */
    public record PlainteRecurrent(String raisonSociale, long count, String firstDate, String lastDate) {
    }

    /** A single complaint row returned by a search on raison sociale or N° ticket. */
    public record PlainteSearchResult(String statut,
                                      String ticketNumber,
                                      String dateOuverture,
                                      String raisonSociale,
                                      String produit,
                                      String clientSkills,
                                      String service,
                                      String priorite,
                                      String etat,
                                      String reparePar,
                                      String responsabilite) {
    }
}