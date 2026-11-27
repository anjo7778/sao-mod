# Stage 2 — Living world (D, F, G, H)

Stage 1 v0.1.6 remains the verified baseline. Experimental v0.2.0 is developed on stage2/settlements. No user testing required for development; cloud CI and real client verification before main integration.

| ID | Task | Source status |
|---|---|---|
| D1 | Town of Beginnings, central spawn | 608×608 plan, flat terrain, explicit plaza spawn, per-chunk building generation |
| D2 | Capital on each upper floor | 384×384 layout and theme material palette |
| D3 | Regional towns, villages, outposts | Pending |
| D4 | City safe zones | Spawn placement and entity-caused damage restrictions; guardian outside wall |
| D5 | Roads, gates, districts and buildings | Prototype grid, seven role-based lot types |
| D6 | Teleport plazas | Existing gated travel targets center; physical portal UI pending |
| D7 | Furnished and functional buildings | Basic interior fixtures; real jobs, service and detailed designs pending |
| F1–F4 | Identity, persistent needs, goals, decisions | Prior pure Java mind; runtime entities and save integration pending |
| G1–G3 | Relationships, memories and dialogue | Prior Java relationship primitives; event dispatch and conversations pending |
| H1–H3 | Schedules, movement, daily activities | Pending |

Capital generation uses stable floor addressing, a city biome, a blended flat density region and one decoration feature writing only the requested chunk. No global placement task or per-floor dimension. The first city uses 608-block square and upper capitals 384 blocks; these sizes are provisional. No NPC populations, real shops, terrain-adapted organic street plans, canals or detailed handcrafted designs yet. Current feature can be affected by other mods' worldgen and neighboring feature spillover. Safety rules cover standard hostile spawn placement and entity-caused attacks, not arbitrary commands, all modded damage sources, environmental damage or total grief protection. Guardian encounters must occur outside safe zones. New worlds only for new generation. Verify actual spawn, adjacent chunks, interiors, city protection, guardian progression, travel and save/reload before acceptance.
