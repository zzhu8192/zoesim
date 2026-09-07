# Simulation Lab

A Java and LibGDX desktop simulation lab containing a Rock / Paper / Scissors battle arena and an animated black-hole accretion simulation.

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
