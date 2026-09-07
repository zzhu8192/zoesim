# Simulation Lab

A Java and LibGDX desktop simulation lab containing a Rock / Paper / Scissors battle arena and an animated black-hole accretion simulation.

## Black-hole model

The black-hole simulation continuously introduces varied planets, stars, asteroids, icy bodies, ringed worlds, nebulae, and comets. Their Newtonian motion is calculated in three dimensions, then projected onto the circular arena: inclined orbital planes can pass in front of and behind the event horizon rather than sharing one screen plane. A dark horizon, thin photon ring, tilted turbulent accretion disk, lensed moving star field, absorption sparks, and false-color bipolar jets provide the visual model. It is a credible visual approximation, not a general-relativity solver.

Key runtime parameters are documented in [SIMULATION_REQUIREMENTS.md](SIMULATION_REQUIREMENTS.md).

Run the desktop application with Maven:

```powershell
mvn exec:java
```

Run the JUnit suite without opening a window:

```powershell
mvn test
```

Run the lightweight model smoke checks explicitly in headless mode:

```powershell
mvn exec:java '-Dexec.args=--headless'
```

Build a distributable jar:

```powershell
mvn package
java -jar target/rps-arena.jar
```

Controls are in the right-hand panel. The generated visuals animate in place, and the simulations use CC0 Kenney sounds with license files included beside the audio assets.
