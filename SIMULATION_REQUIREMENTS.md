# Simulation Lab — Requirements and Handoff Context

## Project

Desktop Java 17 / LibGDX application built with Maven. The application starts at a menu that lets the user select a simulation. It is designed to grow with additional simulations.

Build and run:

```powershell
& 'D:\lang\maven399\bin\mvn.cmd' package
& 'D:\lang\maven399\bin\mvn.cmd' exec:java
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
- Start at a black-hole mass of `2200`. The visual event-horizon radius is `34 + 0.68 × sqrt(mass)`.
- Spawn objects on random three-dimensional orbital planes at 53–79% of circular-orbit speed, plus a 7–23 unit inward component. This produces inclined, decaying elliptical trajectories rather than one flat orbit plane.
- Limit velocity to 340 units per second and apply light velocity damping to keep continuous accretion stable.
- Each absorbed object increases black-hole mass by that object’s mass.
- Absorb an object when its three-dimensional center reaches `eventHorizon + max(1, 0.72 × objectSize)`.
- The event-horizon radius must grow as a monotonic function of black-hole mass.
- A larger horizon also changes the gravity term through the increased mass.
- The black hole must visibly grow in proportion to consumed objects, especially after large objects.
- Seed 180 objects by default; the UI clamps the seed setting to 10–360.
- Continue introducing one object every 0.045 simulation seconds while fewer than 900 objects are active and the horizon is smaller than the arena.
- End the simulation when `eventHorizon >= arenaRadius - 10`; display an explicit end-state message.

### Accretion disk and jets

- The black hole has a procedurally generated, angled (`-17°`) turbulent accretion disk around a dark central horizon. The disk keeps its original elliptical perspective while using the pale white/rose spectrum and photon ring described above.
- Add periodic bipolar plasma jets along the rotational poles (above and below the disk axis):
  - Eject short-lived particles from opposite poles approximately perpendicular to the disk, with a concentrated core and a small angular scatter.
  - Use a false-color synchrotron-style gradient from warmer lower-energy edges through cyan/blue toward a bright core. It is a visualization convention, not a literal claim about visible gamma rays.
  - This is a visual model of relativistic jets; it need not claim a fully relativistic general-relativity simulation.

### Audio

- Play a restrained ominous low-frequency sound when particles are absorbed. Rate-limit it to avoid a wall of sound during high absorption rates.
- Play quiet sci-fi/space ambience while Black Hole mode is active; stop it when leaving the simulation or reaching the terminal state.
- Existing CC0 sources include Kenney Interface Sounds and Kenney Sci-Fi Sounds. Keep their license files in `src/main/resources/sounds`.

### Controls

- `PARTICLE SEED` controls how many objects are present at reset.
- `+` / `-` support click and hold-to-repeat.
- Clicking the numeric value opens typed numeric entry; Enter commits and Escape cancels.
- `NEW CLOUD` resets mass, horizon, counters, and the seeded cloud using the configured count.
- A draggable `TIME SCALE` slider changes simulation speed.
- Pause/resume control.

## Current source layout

- `src/main/java/zoesim/DesktopLauncher.java` — desktop entry point and optional headless smoke-check mode.
- `src/main/java/zoesim/SimArena.java` — thin application shell for lifecycle, layout, menu selection, and simulation dispatch.
- `src/main/java/zoesim/ArenaSimulation.java` — lifecycle contract implemented by each simulation.
- `src/main/java/zoesim/RpsSimulation.java` — complete RPS rules, state, input, rendering, and assets.
- `src/main/java/zoesim/BlackHoleSimulation.java` — complete black-hole physics, state, input, rendering, and assets.
- `src/main/java/zoesim/SimulationMenu.java` — menu rendering and selection.
- `src/main/resources/sounds/` — CC0 audio assets and licenses.
- `images/a.jpg`, `images/b.jpg` — original RPS reference photos.
- `images/blackhole.png`, `images/blackhole2.png` — black-hole/accretion-disk visual references.

## Follow-up quality bar

- Prefer credible visual physics and clear affordances over decorative effects.
- Keep physics and rendering documentation synchronized with the constants and behavior in `BlackHoleSimulation.java` whenever they change.
- Do not reintroduce generic colored-dot score markers or unsupported Unicode glyphs in the default bitmap font (use ASCII `-` for minus).
- Preserve the menu-first workflow and source modularity whenever adding simulations.
