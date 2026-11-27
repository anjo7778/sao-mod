package dev.anjo7778.sao.simulation;

/** Sequential unlock policy. Call only after an authoritative boss victory. */
public final class FloorProgress {
    public enum Victor { PLAYER, NPC_PARTY }
    private long highestUnlocked;
    private final boolean npcUnlocks;

    public FloorProgress(long highestUnlocked, boolean npcUnlocks) {
        if (highestUnlocked < 1) throw new IllegalArgumentException("Invalid progress");
        this.highestUnlocked = highestUnlocked;
        this.npcUnlocks = npcUnlocks;
    }
    public long highestUnlocked() { return highestUnlocked; }
    public boolean isUnlocked(long floor) { return floor > 0 && floor <= highestUnlocked; }
    public boolean recordVictory(long floor, Victor victor) {
        java.util.Objects.requireNonNull(victor);
        if (floor != highestUnlocked || highestUnlocked == Long.MAX_VALUE || (victor == Victor.NPC_PARTY && !npcUnlocks)) return false;
        highestUnlocked++;
        return true;
    }
}
