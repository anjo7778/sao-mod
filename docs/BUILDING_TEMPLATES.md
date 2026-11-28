# Capital building template acceptance

**Policy:** A candidate is not a finished building until it passes structural checks, generated-world checks and visual review. Do not expand a visually rejected template. Keep the exact version used by each world; no automatic regeneration of existing cities.

## Candidate library v1

Eight standalone layouts: cottage, townhouse, inn, market, guildhall, forge, clinic and quest hall. Each is a fixed editable text-layer blueprint under src/main/resources/data/sao/templates/v1/. First line = width height depth. Then height × depth rows of exactly width symbols, in y/z/x order. Coordinates centered horizontally. World generation reads fixed arrays; palette maps explicit symbols to oriented block states. New v0.2.9 city template generation uses these files, with fixed footprints inside old plots; existing v0.2.8 saves retain their previous geometry. Vanilla material palettes, no external assets. Open arch entrances intentionally have no obstructing door leaf. Single usable ground floors with attic roof volumes, not finished multistory buildings. Fixed north-facing placement, not rotated or mirrored yet.

## Required checks per model

1. Parse exact layer dimensions and symbols; validate supported fixtures, bed pairing, roof cover, no blocked entrances, connected walkable cells, reachable workstations and clear NPC destinations. Verify fit in every role assignment and theme.
2. Generate in real Minecraft. Check actual block states at all template positions, including orientations, real bed pairs, lantern support, roof direction, block light and chunk-boundary continuity. Verify standard pathfinding from entrance to service point. Save/reload before full acceptance.
3. Capture exterior and interior viewpoints in Minecraft, including an eye-height view, entrance, and service/furniture area. Inspect screenshots, not just whether a file exists. Record defects and retest after corrections.
4. Only accept verified styles. No automatic mass expansion before acceptance. A future rotation/mirror requires transforming states and verifying its own paths and chunk-boundary tests. Resource pack optional; artistic review remains necessary.

## Status

v1 templates are **candidates**. Programmatic geometry and actual Minecraft checks must run. No finished rotation, shop UI, autonomous bed use or functioning NPC economy claimed.
