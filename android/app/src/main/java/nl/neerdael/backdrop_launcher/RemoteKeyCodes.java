package nl.neerdael.backdrop_launcher;

/** Keeps a vendor remote's Center button from being classified as a modifier. */
final class RemoteKeyCodes {
    private RemoteKeyCodes() {}

    static int scanCodeForFlutter(int keyCode, int scanCode) {
        // UGOOS reports DPAD_CENTER (23) with Linux KEY_RIGHTCTRL (97).
        // Scan code zero lets Flutter resolve its physical key from DPAD_CENTER.
        return keyCode == 23 && scanCode == 97 ? 0 : scanCode;
    }
}
