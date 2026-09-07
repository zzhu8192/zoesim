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

Use `images/blackhole.png` as the visual direction:

- Predominantly black event horizon.
- Bright, hot, tilted accretion disk with white, warm gold, orange, and restrained blue/gray light.
- The visual should not resemble a purple glowing circle or a generic rainbow vortex.

### Celestial objects

- Particles should look like small planets, stars, icy bodies, rocky asteroids, or ringed worlds—not pure RGB/rainbow dots.
- Use a varied natural/celestial palette: rocky browns, ice blues, muted gas-giant golds, off-white stars, and rusty asteroid tones.
- Objects must vary noticeably in size and visual treatment/shape.
- Larger objects have larger mass. In the current model mass is proportional to cross-sectional area (`radius²`).
- Objects must stay inside the circular arena until absorbed.

### Physics and growth

- Use a Newtonian inverse-square gravitational field:
  - `a = μ / r²`, where `μ = G × blackHoleMass`.
  - A small softening radius is acceptable for numerical stability near the center.
- Spawn objects at sub-orbital speeds with a small inward component so they are bound and can accrete.
- Each absorbed object increases black-hole mass by that object’s mass.
- The event-horizon radius must grow as a monotonic function of black-hole mass.
- A larger horizon also changes the gravity term through the increased mass.
- The black hole must visibly grow in proportion to consumed objects, especially after large objects.
- Continue populating the arena while the horizon is smaller than the arena.
- End the simulation only when the event horizon fills the arena; display an explicit end-state message.

### Accretion disk and jets

- The black hole has an angled, bright accretion disk around a dark central horizon.
- Add periodic bipolar plasma jets along the rotational poles (above and below the disk axis):
  - Eject short-lived blue-white particles outward from opposite poles.
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

- `src/main/java/mspacman/DesktopLauncher.java` — LibGDX desktop entry point.
- `src/main/java/mspacman/RpsArena.java` — application shell, rendering lifecycle, UI wiring, and shared assets. This remains the main integration point and should be further thinned as simulations grow.
- `src/main/java/mspacman/RpsSimulation.java` — RPS rules module.
- `src/main/java/mspacman/BlackHoleSimulation.java` — black-hole mass, gravity parameter, and horizon equations.
- `src/main/java/mspacman/SimulationMenu.java` — menu rendering and selection.
- `src/main/resources/sounds/` — CC0 audio assets and licenses.
- `images/a.jpg`, `images/b.jpg` — original RPS reference photos.
- `images/blackhole.png` — black-hole/accretion-disk visual reference.

## Follow-up quality bar

- Prefer credible visual physics and clear affordances over decorative effects.
- Do not reintroduce generic colored-dot score markers or unsupported Unicode glyphs in the default bitmap font (use ASCII `-` for minus).
- Preserve the menu-first workflow and source modularity whenever adding simulations.
