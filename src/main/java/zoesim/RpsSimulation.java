package zoesim;

/** Rules isolated from the application shell so the RPS simulation can evolve independently. */
public final class RpsSimulation {
    private RpsSimulation() { }

    /** Kind ordinals: rock=0, paper=1, scissors=2. */
    public static boolean beats(int attacker, int defender) {
        return (attacker == 0 && defender == 2) || (attacker == 1 && defender == 0) || (attacker == 2 && defender == 1);
    }
}
