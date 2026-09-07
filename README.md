# RPS Arena

A real-time desktop Rock / Paper / Scissors battle simulator built with Java and LibGDX. Each winning piece consumes its opponent and splits into two, so the counters always show the live population inside the arena.

Run it with Maven:

```powershell
mvn exec:java
```

Build a distributable jar:

```powershell
mvn package
java -jar target/rps-arena.jar
```

Controls are in the right-hand panel: adjust the three starting populations, drag the motion-speed slider, pause, or reset. The generated sprites animate in place (including a three-frame scissor cut), and collisions use CC0 Kenney Interface Sounds; its license is included beside the WAV files.
