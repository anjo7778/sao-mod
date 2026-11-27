package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;

/** No Minecraft runtime or external test dependencies required. */
public final class SimulationChecks {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        FloorCatalog a = new FloorCatalog(42), b = new FloorCatalog(42);
        Set<Long> seeds = new HashSet<>();
        for (long i = 1; i <= 10_000; i++) {
            var f = a.describe(i);
            require(f.equals(b.describe(i)), "Reproducibility at floor " + i);
            require(seeds.add(f.seed()), "Duplicate floor seed");
            require(f.radiusBlocks() >= 800 && f.radiusBlocks() <= 4000, "Invalid radius");
            require(Double.isFinite(f.threat()) && f.threat() >= 1, "Invalid threat");
        }
        require(a.describe(1).theme() == FloorCatalog.Theme.PLAINS, "Starting floor");
        require(a.describe(1).seed() != new FloorCatalog(43).describe(1).seed(), "World seed ignored");
        require(a.describe(Long.MAX_VALUE).radiusBlocks() == 800, "Overflow");
        FloorProgress progress = new FloorProgress(1, true);
        require(!progress.recordVictory(2, FloorProgress.Victor.PLAYER), "Skipped locked floor");
        require(progress.recordVictory(1, FloorProgress.Victor.NPC_PARTY), "NPC victory rejected");
        require(!progress.recordVictory(1, FloorProgress.Victor.NPC_PARTY), "Repeated victory");
        require(progress.isUnlocked(2) && !progress.isUnlocked(3), "Unlock boundary");
        require(!new FloorProgress(1, false).recordVictory(1, FloorProgress.Victor.NPC_PARTY), "NPC policy ignored");
        require(!new FloorProgress(Long.MAX_VALUE, true).recordVictory(Long.MAX_VALUE, FloorProgress.Victor.PLAYER), "Overflow");
        var mind = new AdventurerMind(new Personality(80, 70, 50, 90));
        UUID player = new UUID(1, 1), other = new UUID(1, 2);
        var rescue = new Memory(new UUID(2, 1), player, 3, EventType.RESCUED);
        require(mind.remember(rescue) && !mind.remember(rescue), "Memory idempotency");
        require(mind.relationship(player).trust() == 75 && mind.relationship(other).trust() == 50, "Actor-specific trust");
        var reloaded = new AdventurerMind(mind.personality());
        mind.memories().forEach(reloaded::remember);
        require(reloaded.relationship(player).equals(mind.relationship(player)), "Snapshot replay");
        mind.remember(new Memory(new UUID(2, 2), player, 4, EventType.ABANDONED));
        require(mind.relationship(player).trust() == 40, "Abandonment response");
        require(mind.choose(new Needs(10, 100, 100, 100, 0)) == Action.REST, "Health safety");
        require(mind.choose(new Needs(100, 5, 100, 100, 0)) == Action.RESUPPLY, "Supply safety");
        require(mind.choose(new Needs(100, 100, 5, 100, 0)) == Action.REPAIR, "Repair safety");
        require(mind.choose(new Needs(100, 100, 100, 100, 0)) == Action.EXPLORE, "Ready adventurer");
        require(mind.choose(new Needs(100, 100, 100, 10, 100)) != Action.EXPLORE, "Unsafe exploration");
        for (int i = 0; i < 200; i++) mind.remember(new Memory(new UUID(3, i), other, i+5, EventType.SHARED_LOOT));
        require(mind.memories().size() == 128, "Unbounded memory");
        require(mind.relationship(other).trust() == 100, "Trust clamp");
        System.out.println("PASS: " + checks + " simulation assertions across 10,000 floor descriptions");
    }
}
