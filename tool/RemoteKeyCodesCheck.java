package nl.neerdael.backdrop_launcher;

public final class RemoteKeyCodesCheck {
    public static void main(String[] args) {
        int[][] cases = {
            {23, 97, 0},   // Observed UGOOS Center event.
            {23, 0, 0},    // Standard injected Center.
            {23, 353, 353},// Other remote's normal Select scan code.
            {114, 97, 97}, // A real Right Ctrl key must remain unchanged.
            {66, 28, 28},  // Enter.
            {4, 158, 158}, // Back.
            {22, 106, 106} // Right arrow.
        };
        for (int[] row : cases) {
            int actual = RemoteKeyCodes.scanCodeForFlutter(row[0], row[1]);
            if (actual != row[2]) throw new AssertionError("Unexpected scan code for key " + row[0]);
        }
        System.out.println("7 remote scan-code cases passed.");
    }
}
