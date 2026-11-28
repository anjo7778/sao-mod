# Aincrad interface direction — approved reference

Primary external reference: [SAO Utils: Beta](https://store.steampowered.com/app/786520/SAO_Utils_Beta/). Secondary reference: [SAO Utils 2: Progressive](https://store.steampowered.com/app/877280/SAO_Utils_2_Progressive/). Both store pages describe a 3D launcher summoned by holding left and right mouse buttons and sliding down. Use the visual and interaction reference, not the desktop launcher as a dependency. Recreate the intended Aincrad aesthetic with original code, icons, layout assets and sounds; do not bundle extracted anime or third-party launcher assets. No claim of pixel-perfect fidelity before actual screenshot review.

## Target experience

- Floating, translucent white/light-blue panels, orange selection accents, vertical circular category icons and side-opening submenus, smooth perspective, opening/closing and selection transitions.
- World-space-style presentation as the target, with readable screen-space fallback for accessibility. Preserve camera and pointer control, scaling across resolutions/FOV, no clipping through near-plane or illegible panels, no shader dependency. The interface is non-pausing by default; gameplay continues while it is open. Provide a conventional configurable keybind too.
- Optional immersive first- and third-person two-finger downward swipe animation, followed by animated selection. NPC menu gestures should eventually correspond to actual equipment/quest/skill changes, not pretend actions. Do not delay a usable menu for full skeletal finger animation.
- Mouse chord (LMB+RMB) plus downward movement opens menu. Resolve attack/use/block conflicts with a deliberate threshold, short gesture arming window, release/cancel handling and configurable binding. Avoid swallowed ordinary clicks, accidental menu activation while fighting, stuck attack/use state or uncontrolled camera movement. Escape closes and restores input; keybind bypasses gesture. Test with other mods, controller alternatives later.
- Unified navigation: Character, Inventory/Equipment, Weapon Skills, Professions, Quests, Party, Guild, Map/Travel, Friends/Social, Marketplace and Settings. Add health/stamina/mana/XP, notifications and NPC inspect panels using the same visual language. Features not implemented must be explicitly unavailable, not fabricated values or pretend interactions.
- Multiple draggable windows and contextual item/NPC inspection later, with bounded window count, scaling and predictable focus order.
- The client renders synchronized server state. Any allocation, skill activation, equipment action, quest acceptance or trade must be a validated server action. Never trust client-reported attributes, XP, money or inventory.

## Incremental order

1. Preserve known verified Stage 2 functionality. Diagnose the failed archery and Frenzy Boar client probes (builds passed but runtime acceptance failed). Re-run with actionable crash diagnostics and visual evidence.
2. Complete shared RPG progression: v0.2.24 build passed; authenticate real combat/quest/discovery/crafting event sources and replay rules, wire player allocation and NPC allocation, and implement fixed monster difficulty bands. No fictitious XP for unfinished systems.
3. In parallel as a small vertical slice, implement a native non-pausing gesture menu with category animation, input conflict handling and a real Character panel backed by authoritative XP and mastery data. Start with existing real data; add inventory only when actual server-backed equipment actions exist. Prototype projection separately from data screens so UI architecture does not require later rewrites.
4. Develop shared grip/loadout rules and actual combat state (stamina, dodge, block, parry, combos) then representative weapon trees: sword, shield, dual swords, bow, scythe, plus blacksmithing. Add the corresponding interactive UI sections as each feature becomes authoritative.
5. Expand equipment, professions, monster variety, quests, parties/guilds, non-hardcore defeat and economy. Add corresponding menu panels incrementally, then NPC interaction animations, HUD, optional simultaneous windows and final visual polish.

## Acceptance

First menu test must open/close by real gesture and fallback key, not pause simulation, restore normal combat/use controls, show actual synchronized character state, handle scale/resolution and disable unsupported actions. Test first-person rendering and screenshot comparison to the chosen reference; separate visual approval from functional acceptance. Keep original Minecraft menus usable as fallback until each replacement is verified. No new mandatory dependencies. This is a design specification, not implemented UI.

## v0.2.26 first native menu (candidate)

Non-pausing screen-space prototype, translucent panels, circular navigation, orange selection, short opening animation, M fallback key, LMB+RMB downward movement chord (provisional raw-cursor/pitch threshold), normal-world eligibility from server, and escape close. Actual server profile snapshot includes character XP, level, attributes and independent weapon/profession masteries; + buttons send one-point allocation to the server with sequence protection. All other sections are labeled unavailable. No client-authoritative stats. The chord suppresses attack/use events while both buttons are down, but the first ordinary click may still have occurred before arming; full input/camera choreography, finger animation, world-space projection, draggable windows, authentic icon assets and accessibility are unfinished. Compilation, live gesture, input release, allocation and screenshot acceptance pending.

## Verified v0.2.26 functionality

[Graphical runtime probe 46097100943](https://github.com/anjo7778/sao-mod/actions/runs/46097100943) passed: actual capital discovery and four player-attributed hostile kills produced 100 XP, level 2, sword 80 XP; native M key and LMB+RMB downward movement opened the real interface; world time advanced; attribute click changed server Strength to 11 and points to 2; saved state round trip passed; Escape closed. [Four screenshot artifacts](https://github.com/anjo7778/sao-mod/actions/runs/46097100943/artifacts/15244031104) captured but visual appearance not reviewed. Hand animations and 3D projection remain pending.
