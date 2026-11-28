package dev.anjo7778.sao.simulation;

import java.util.*;

/** A bounded, deterministic social memory and utility policy, independent of Minecraft entities. */
public final class AdventurerMind {
    public enum Action { REST, RESUPPLY, REPAIR, RECRUIT, TRAIN, EXPLORE, SOCIALIZE }
    public enum EventType { RESCUED, SHARED_LOOT, FOUGHT_TOGETHER, ABANDONED, GREETED, CONVERSED, DISAGREED }
    public record Personality(int courage, int loyalty, int sociability, int ambition) {
        public Personality { check(courage); check(loyalty); check(sociability); check(ambition); }
    }
    public record Needs(int health, int supplies, int equipment, int partyReadiness, int danger) {
        public Needs { check(health); check(supplies); check(equipment); check(partyReadiness); check(danger); }
    }
    public record Memory(UUID eventId, UUID actor, long day, EventType type) {
        public Memory {
            Objects.requireNonNull(eventId); Objects.requireNonNull(actor); Objects.requireNonNull(type);
            if (day < 0) throw new IllegalArgumentException("Negative day");
        }
    }
    public record Relationship(int trust, int friendship, int respect) {}
    private static final int MEMORY_LIMIT = 128;
    private final Personality personality;
    private final ArrayDeque<Memory> memories = new ArrayDeque<>();
    private final Set<UUID> rememberedIds = new HashSet<>();

    public AdventurerMind(Personality personality) { this.personality = Objects.requireNonNull(personality); }
    public Personality personality() { return personality; }
    public List<Memory> memories() { return List.copyOf(memories); }

    public boolean remember(Memory memory) {
        Objects.requireNonNull(memory);
        if (!rememberedIds.add(memory.eventId())) return false;
        memories.addLast(memory);
        if (memories.size() > MEMORY_LIMIT) rememberedIds.remove(memories.removeFirst().eventId());
        return true;
    }

    public Relationship relationship(UUID actor) {
        Objects.requireNonNull(actor);
        int trust = 50, friendship = 0, respect = 0;
        for (Memory memory : memories) {
            if (!actor.equals(memory.actor())) continue;
            switch (memory.type()) {
                case CONVERSED -> { trust += 2; friendship += 3; }
                case DISAGREED -> { trust -= 3; friendship -= 2; }
                case GREETED -> { trust += 1; friendship += 2; }
                case RESCUED -> { trust += 25; friendship += 20; respect += 20; }
                case SHARED_LOOT -> { trust += 10; friendship += 12; }
                case FOUGHT_TOGETHER -> { trust += 8; friendship += 8; respect += 10; }
                case ABANDONED -> { trust -= 35; friendship -= 25; respect -= 15; }
            }
        }
        return new Relationship(clamp(trust), clamp(friendship), clamp(respect));
    }

    public Action choose(Needs n) {
        Objects.requireNonNull(n);
        if (n.health() < 35) return Action.REST;
        if (n.supplies() < 20) return Action.RESUPPLY;
        if (n.equipment() < 20) return Action.REPAIR;
        int explore = 35 + personality.courage()/3 + personality.ambition()/3 + n.partyReadiness()/4 - n.danger()/2;
        if (n.danger() > 80 && n.partyReadiness() < 60) explore = Integer.MIN_VALUE;
        int[] score = {100-n.health(), 100-n.supplies(), 100-n.equipment(),
                (100-n.partyReadiness())/2+personality.sociability()/3+personality.loyalty()/5,
                20+personality.ambition()/3, explore, 15+personality.sociability()/2};
        int best = 0;
        for (int i = 1; i < score.length; i++) if (score[i] > score[best]) best = i;
        return Action.values()[best];
    }

    private static int clamp(int n) { return Math.max(0, Math.min(100, n)); }
    private static void check(int n) { if (n < 0 || n > 100) throw new IllegalArgumentException("Expected 0..100"); }
}
