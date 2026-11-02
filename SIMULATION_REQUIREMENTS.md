# Simulation Lab — Requirements and Handoff Context

## Project

Desktop Java 17 / LibGDX application built with Maven. The application starts at a menu that lets the user select a simulation. It is designed to grow with additional simulations.

Build and run:

```bash
mvn package
mvn exec:java
```

The runnable shaded jar is `target/rps-arena.jar`.

## Shared product requirements

- Start on a simulation-selection menu, not directly in a simulation.
- Each simulation must offer a clear route back to the menu.
- Keep simulations in distinct source files as the project evolves. The menu also has its own source file.
- The interface should be readable at desktop/TV distance: use large type and high contrast.
- Numeric steppers must support single click, press-and-hold repeat, and direct typed entry. Enter commits typed values; Escape cancels an edit.
- Sliders must support click and drag, not click-only interaction.
- Keep visible counters synchronized with actual simulation objects, rather than using unrelated colored-dot indicators.
- Use licensed/free local assets for audio and retain their license text alongside the files.

## 1. Rock / Paper / Scissors Arena

### Goal

Simulate a real-time battle of rock, paper, and scissors within a circular arena. The last surviving type wins.

### Rules

- Rock beats scissors.
- Paper beats rock.
- Scissors beat paper.
- When a winning piece touches a losing piece:
  - The losing piece disappears.
  - The winning piece splits into two pieces of its own type.
  - The count therefore changes by `+1` for the winner type and `-1` for the loser type.
- The live counters must equal the number of pieces currently inside the arena.
- The simulation caps total pieces to avoid runaway performance cost.

### Movement

- Pieces stay inside the circle and bounce at its edge.
- Speed should not be perfectly constant:
  - Pieces gently vary around a cruise speed.
  - New split pieces receive a short acceleration burst.
  - Wall bounces apply a small, temporary deceleration.
- The user can control global movement speed through a draggable slider.

### Presentation

- Pieces are transparent-background sprites generated locally at runtime, not colored circles or outlined primitives.
- Rock should read as a textured irregular stone.
- Paper should read as a folded, lined paper sheet.
- Scissors should use multiple animation frames so they visibly cut/open/close while moving.
- The right-side scoreboard uses those same piece sprites instead of generic colored dots.
- Collision effects should resemble discrete firework sparks: individual flickering particles following gravity-driven arcs, without long connected streak lines.

### Audio

- Rock collision: stone-like knock.
- Paper collision: paper/rustle-wrap sound.
- Scissors collision: sharp snip/cut sound.
- Existing effects use CC0 Kenney Interface Sounds under `src/main/resources/sounds`.

### Controls

- Starting population controls for rock, paper, and scissors.
- `+` / `-` support hold-to-repeat.
- Click the numeric value and type a new number; press Enter to apply.
- Draggable speed slider.
- Pause/resume and reset.

## 2. Black Hole Accretion Simulation

### Goal

Show a black hole consuming an ongoing supply of celestial objects. This is not a finite particle cloud: particles continue entering the field until the black hole’s event horizon expands to fill the arena.

### Visual reference

Use `images/blackhole.png` and the newer `images/blackhole2.png` as visual direction. The renderer remains procedural; neither image is drawn into the simulation.

- Predominantly black event horizon.
- Bright, hot, tilted accretion disk with a natural elliptical perspective, pale white/rose light, and restrained warm/cool variation.
- Retain a thin photon ring at the horizon boundary; avoid straight equatorial beams, static concentric outlines, and generic purple/blue boundary circles.
- Sub-pixel anti-aliased event horizon boundary: procedural 1024x1024 texture map with analytic cubic Hermite smoothstep blending ($C^1$ continuity) across a sub-pixel transition band ($aaBand = 2.4\text{px}$), eliminating texel blockiness, bilinear grid faceting, and stair-stepping even when expanded to full-screen scales.
- Use a soft halo, turbulent moving knots, and background-star lensing to suggest strong gravity without claiming a general-relativity render.
- The visual should not resemble a purple glowing circle or a generic rainbow vortex.

### Celestial objects

- Particles should look like small planets, stars, icy bodies, rocky asteroids, or ringed worlds—not pure RGB/rainbow dots.
- Use a varied natural/celestial palette: rocky browns, ice blues, muted gas-giant golds, off-white stars, and rusty asteroid tones.
- Objects must vary noticeably in size and visual treatment/shape.
- Larger objects have larger mass. In the current model mass is proportional to cross-sectional area (`radius²`).
- Object state is three-dimensional (`x`, `y`, `z` position and velocity). The arena is the orthographic screen projection of that volume: a positive depth object renders in front of the horizon and a negative depth object renders behind it.
- Objects remain within the spherical simulation boundary until absorbed; their projected positions remain inside the circular arena.

### Physics and growth

- Use a Newtonian inverse-square gravitational field in three dimensions:
  - `a = μ / r²`, where `μ = G × blackHoleMass`, `G = 220`, and `r² = dx² + dy² + dz²`.
  - Acceleration uses a squared softening floor of `900` (a 30-unit softening length) for numerical stability near the center.
- Start at a black-hole mass of `2200`. The visual event-horizon radius starts at `18 + 0.32 × sqrt(mass)` (~33 units at start, approximately half its original size).
- Spawn objects across true three-dimensional orbital planes using orthonormal basis vector projection $(\mathbf{u}, \mathbf{v})$ from normal $\mathbf{n}$:
  - ~35% co-planar with the accretion disc ($-17^\circ$ visual plane with $\pm 8^\circ$ dispersion).
  - ~40% moderate 3D orbital inclinations ($15^\circ$ to $55^\circ$).
  - ~25% high-inclination & polar orbits slicing across the polar jetstream axis and full $Z$-depth.
- Particles carry authentic 3D circular velocity $\mathbf{v}_{\text{tangent}} = (-\mathbf{u}\sin\alpha + \mathbf{v}\cos\alpha)v_{\text{orb}} \cdot \text{dir}$ with scaled Keplerian speed ($2.25\times$) and inward drift ($-5.5$ to $-14$ units/s), yielding lively orbits (2–4 revolutions per body).
- Depth cueing reinforces 3D volume: celestial bodies scale from $0.74\times$ to $1.26\times$ based on depth $z$, receive direct foreground illumination or background atmospheric dimming ($0.60\times$ to $1.05\times$), and sort behind/in front of the accretion disc and event horizon.
- Particles physically accelerate up to 950 units per second as they approach the event horizon, driven by 5.06x scaled Newtonian inverse-square gravity and an enhanced near-horizon relativistic acceleration bonus ($1 + 2.4 \times r_h^2 / r^2$).
- Each absorbed object increases black-hole mass by `particleMass * growthRate * 5.0f` (5x base growth multiplier), ensuring continuous visible expansion as celestial objects spiral inward.
- Absorb an object when its three-dimensional center reaches `eventHorizon + max(1, 0.72 × objectSize)`.
- The event-horizon radius grows as a monotonic function of black-hole mass.
- A larger horizon also changes the gravity term through the increased mass.
- The black hole visibly grows in proportion to consumed objects, especially after large objects.
- Individual celestial body densities and mass scales (`0.22`–`0.85`) across archetypes keep individual masses calibrated so single absorptions expand the horizon smoothly rather than triggering runaway spikes.
- Initial seed cloud (180 bodies default, clamped to 10–360) is evenly distributed across radial orbital zones from near-horizon to outer space, providing steady incremental accretion from the first frame. Continuous spawns use staggered inflow across outer-to-mid cosmic orbits to maintain an unbroken stream of inward-spiraling matter.
- Cosmic field radius spans the full screen extent (`fieldRadius = hypot(maxDx, maxDy)`) rather than being artificially restricted to the old 1/4-screen battle circle (`arenaRadius`). Celestial bodies orbit and lensed stars span across the entire screen.
- End the simulation when `eventHorizon >= fieldRadius - 10`; display an explicit centered end-state message once the black hole event horizon has truly expanded to engulf the entire screen.

### Accretion disk and jets

- The black hole has a procedurally generated, angled (`-17°`) wide, eye-shaped luminous accretion disc (reference `images/accretion_disc.png`):
  - Extends out horizontally to over $4\times$ the event horizon radius in an elongated eye/almond profile, tapering into slender tips at the left and right edges where it diminishes in brightness and density.
  - Dynamic front crossing with zero static lines: the pitch-black event horizon silhouette is unmarred by static baked lines; all front crossing is rendered dynamically by the spinning plasma clouds.
  - Features an organic circular cloudy light ring directly surrounding the event horizon, extending outward by ~1/3 of the black hole radius ($r \approx 1.0 \dots 1.34 R_h$), fading smoothly in brightness and density with multi-frequency turbulent smoke texture and zero artificial edge cutoff.
  - Features a razor-thin circular photon ring right at the photon sphere with sub-pixel Hermite anti-aliasing.
  - Rendered as a single unified **spinning cloud of light** (1,400+ volumetric plasma puffs) with additive blending (`GL_SRC_ALPHA, GL_ONE`), multi-frequency spiral eddies, and organic Gaussian density falloff (zero vector lines), matching the texture of the circular light halo and ejection jets.
  - High-luminance white gamma: the inner disc blazes **3x brighter** with pure diamond/platinum white light, transitioning smoothly through radiant champagne gold in the mid-disc to warm amber/orange at the outer rim.
  - Differential rotation: spins **3x faster on the inside** ($\omega \approx 7.4\text{ rad/s}$ near the horizon, matching infalling celestial bodies) and smoothly gradients out to $\omega \approx 0.36\text{ rad/s}$ at the outer rim.
  - Direct timescale coupling: disc rotation is synchronized with simulation physics (`simTime`), scaling dynamically from `0.1x` to `10.0x` and freezing on pause. Infalling celestial bodies experience near-horizon viscous coupling/drag to match the inner disc speed.
- Continuous progressive bipolar relativistic plasma jetstreams along the rotational poles (reference `images/blackhole_ejection.png`):
  - Forms as a soft, mild, semi-transparent lavender/violet cloud around the 3D polar funnel region.
  - Progressively collimates and intensifies outward into a dense, solid/cloudy plasma beam (purely organic cloudy puffs without artificial straight line/polygon shapes).
  - Features radiant shock diamond pinches with intense starburst flares, before dispersing and darkening naturally into deep space.
- 10 distinct, multi-chromatic procedural 3D spherical celestial archetypes scaled realistically (size halved across all types):
  - Continental Earth-like worlds, Martian rust deserts, glowing volcanic magma rifts.
  - Jovian gas giants with multi-layered turbulent storm belts, zonal eddy flows, Great Red Spot vortex, and realistic 3D inclined concentric rings (inner B-ring, Cassini division, translucent A-ring) rendered with occluded front/back depth arcs.
  - Radiant solar protostars with golden convective photosphere granules, Europa cracked ice moons, chondrite asteroids with crater rims and metallic flecks, Plutonian dwarfs, and azure ice giants (deep atmospheric banding and high methane cirrus).
  - Comet nuclei with radiant spherical coma and tapering multi-segment cyan/blue ion and dust tail streaming away from trajectory.
  - Continuous 360-degree axial rotation across all celestial bodies.

### Audio

- Play an ominous low-frequency sound when particles are absorbed (rate-limited, default volume 0.44).
- Play space ambience while Black Hole mode is active (default volume 0.15); stop it when leaving the simulation or reaching the terminal state.
- Existing CC0 sources include Kenney Interface Sounds and Kenney Sci-Fi Sounds. Keep their license files in `src/main/resources/sounds`.

### Controls

- `PARTICLE SEED` controls how many objects are present at reset.
- `+` / `-` support click and hold-to-repeat.
- Clicking the numeric value opens typed numeric entry; Enter commits and Escape cancels.
- `NEW CLOUD` resets mass, horizon, counters, and the seeded cloud using the configured count.
- Draggable `TIME SCALE` slider: variable simulation pace from `0.1x` to `10.0x`.
- Draggable `GROWTH RATE` slider: controls mass accretion rate on impact from `0.2x` to `8.0x`.
- Pause/resume control.

## Current source layout

- `src/main/java/zoesim/DesktopLauncher.java` — desktop entry point and optional headless smoke-check mode.
- `src/main/java/zoesim/SimArena.java` — thin application shell for lifecycle, layout, menu selection, and simulation dispatch.
- `src/main/java/zoesim/ArenaSimulation.java` — lifecycle contract implemented by each simulation.
- `src/main/java/zoesim/RpsSimulation.java` — complete RPS rules, state, input, rendering, and assets.
- `src/main/java/zoesim/blackhole/` — modular black-hole simulation package:
  - `BlackHolePhysics.java` — pure math and physics constants, gravitational formulas, and lensing calculations.
  - `CosmicParticle.java`, `JetParticle.java`, `AbsorptionFlare.java`, `BackgroundStar.java` — particle & celestial entity models.
  - `BlackHoleAudio.java` — tidal absorption and ambient sound manager.
  - `BlackHoleWorld.java` — numerical integration, 3D particle kinematics, and accretion state.
  - `BlackHoleRenderer.java` — procedural sprite generation, accretion disk, relativistic jets, and celestial rendering.
  - `BlackHoleUI.java` — HUD telemetry, numeric steppers, speed slider, and input controller.
  - `BlackHoleSimulation.java` — top-level `ArenaSimulation` coordinator and backward-compatible facade.
- `src/main/java/zoesim/SimulationMenu.java` — menu rendering and selection.
- `src/main/resources/sounds/` — CC0 audio assets and licenses.
- `images/blackhole.png`, `images/blackhole2.png` — black-hole/accretion-disk visual references.

## Follow-up quality bar

- Prefer credible visual physics and clear affordances over decorative effects.
- Keep physics and rendering documentation synchronized with the constants and behavior in `BlackHoleSimulation.java` whenever they change.
- Do not reintroduce generic colored-dot score markers or unsupported Unicode glyphs in the default bitmap font (use ASCII `-` for minus).
- Preserve the menu-first workflow and source modularity whenever adding simulations.
