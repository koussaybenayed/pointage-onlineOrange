package com.pointage.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fixed official list of members per team. The dashboard only shows these names
 * and ticket statistics are filtered to them.
 */
public final class OfficialTeam {

    private OfficialTeam() {
    }

    public static final List<String> B2B_MEMBERS = List.of(
            "Afef Ayari",
            "Lobna hammami",
            "Nabil Thouri",
            "Med Ali Essifi",
            "Mohamed Ferjene",
            "Fatma Mejri"
    );

    public static final List<String> GP_MEMBERS = List.of(
            "Aamer Hammami",
            "Mehdi Cheffi",
            "Yosr Gharbi",
            "Mohamed ben Marzouk"
    );

    private static final Map<User.Team, List<String>> BY_TEAM = Map.of(
            User.Team.B2B, B2B_MEMBERS,
            User.Team.GP, GP_MEMBERS
    );

    public static List<String> membersOf(User.Team team) {
        return new ArrayList<>(BY_TEAM.getOrDefault(team, List.of()));
    }

    /** Case-insensitive list set lookup keyed by normalized full name. */
    public static Map<String, String> officialNameKeys(User.Team team) {
        Map<String, String> map = new HashMap<>();
        for (String name : membersOf(team)) {
            map.put(normalize(name), name);
        }
        return map;
    }

    public static boolean isOfficial(User.Team team, String fullName) {
        return fullName != null && officialNameKeys(team).containsKey(normalize(fullName));
    }

    /** Return the official capitalization of a matched name, or the input if unknown. */
    public static String officialName(User.Team team, String fullName) {
        if (fullName == null) return null;
        return officialNameKeys(team).getOrDefault(normalize(fullName), fullName);
    }

    public static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
