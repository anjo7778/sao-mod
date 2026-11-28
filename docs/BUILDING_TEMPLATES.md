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

## v1 evidence and v2 revision

v1 actual client acceptance failed at inn block light (5; threshold 6). Cottage and townhouse passed block-state/path/light checks, but visual review found sparse interiors and wide open lots. v1 real restart passed 134,224 block states on two floors. v2 is a separate candidate library: all original files remain in v1, new assets are under templates/v2, and new worldgen keys explicitly select v2. Minimum conservative modeled block light 8 at walkable floor positions; actual client threshold must not be weakened. The inn uses separated sleeping alcoves and added common-room furniture. Repeat actual runtime and restart acceptance; no approved models yet.

## v2 structural verification and visual review

Client run 46087150117 passed all eight exact generated floor-one layouts, real entrance pathfinding and minimum actual block light 9–10. All 19 captures were inspected. Dedicated run 46087169590 preserved 134,224 template block states and a block edit across a real restart. The v2 inn is divided into common space and separate alcoves. Architectural shapes and most service furnishings are still simple; not final artistic approval. v0.2.11 layout prototype may reuse these exact structurally verified v2 models; do not modify or rotate them. Add no new template variants until their own acceptance tests pass.

## Public-space candidates
v0.2.12 adds four fixed outdoor court layouts in reserved gaps. Keep v2 building assets frozen. Courts must pass exact-state checks, support checks and real pathfinding, plus exterior inspection. No new building template or rotations claimed approved. No signs with fabricated quest text, free rewards, automatic trade, or fluid spillover.
