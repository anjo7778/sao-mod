package dev.anjo7778.mtstestbridge;

import com.google.gson.*;
import java.util.Set;

/** Fixed local protocol; no executable commands, arbitrary paths, URLs or remote code. */
record Request(String id, String scenario, String world, long seed, int intervalTicks, int samples, long issuedAt) {
    static final Set<String> FIELDS = Set.of("protocol", "id", "scenario", "world", "seed", "interval_ticks", "samples", "issued_at");
    static Request parse(String raw, long now) {
        JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
        if (!o.keySet().equals(FIELDS) || number(o, "protocol") != 1) throw new IllegalArgumentException("Unsupported request schema");
        String id = string(o, "id"), scenario = string(o, "scenario"), world = string(o, "world");
        if (!id.matches("[a-f0-9]{32}") || !Set.of("terrain","animation","benchmark").contains(scenario)
                || !Set.of("normal","sao","flat").contains(world))
            throw new IllegalArgumentException("Unsupported scenario or identifier");
        long interval = number(o,"interval_ticks"), samples = number(o,"samples"), issued = number(o,"issued_at");
        if (interval < 20 || interval > 200 || samples < 2 || samples > 12 || issued < now - 1800 || issued > now + 60)
            throw new IllegalArgumentException("Request outside safe bounds or expired");
        if (scenario.equals("animation") && !world.equals("flat")) throw new IllegalArgumentException("Animation fixture requires a flat test world");
        return new Request(id, scenario, world, number(o,"seed"), (int)interval, (int)samples, issued);
    }
    private static String string(JsonObject o,String k) {
        var p=o.getAsJsonPrimitive(k);
        if(p==null || !p.isString()) throw new IllegalArgumentException(k);
        return p.getAsString();
    }
    private static long number(JsonObject o,String k) {
        var p=o.getAsJsonPrimitive(k);
        if(p==null || !p.isNumber() || !p.getAsString().matches("-?(0|[1-9][0-9]{0,18})")) throw new IllegalArgumentException(k);
        return Long.parseLong(p.getAsString());
    }
    String saveId() { return "mts-bridge-" + id; }
    String worldName() { return "MTS Test " + id; }
    int frameCount() { return scenario.equals("animation") ? 24 : scenario.equals("terrain") ? 6 : samples; }
    int frameInterval() { return scenario.equals("animation") ? 2 : intervalTicks; }
}
