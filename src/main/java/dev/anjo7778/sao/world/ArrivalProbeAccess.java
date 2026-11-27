package dev.anjo7778.sao.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Test-branch-only access to the production arrival search. */
public final class ArrivalProbeAccess {
    private ArrivalProbeAccess(){}
    public static BlockPos find(ServerLevel level,int x,int z){return FloorTravel.safeArrival(level,x,z);}
}
