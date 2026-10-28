import android.content.ComponentName;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Shell-side preservation of the Android Home preference during documentation. */
public final class GuideHomeState {
    public static void main(String[] args) throws Exception {
        Object manager = Class.forName("android.app.AppGlobals").getDeclaredMethod("getPackageManager").invoke(null);
        if (args.length == 1 && args[0].equals("get")) {
            Method get = manager.getClass().getMethod("getHomeActivities", List.class);
            get.setAccessible(true);
            ComponentName component = (ComponentName) get.invoke(manager, new ArrayList<>());
            System.out.println(component == null ? "none" : component.flattenToShortString());
        } else if (args.length == 2 && args[0].equals("component")) {
            Method get = manager.getClass().getMethod("getComponentEnabledSetting", ComponentName.class, int.class);
            get.setAccessible(true);
            System.out.println(get.invoke(manager, ComponentName.unflattenFromString(args[1]), 0));
        } else if (args.length == 2 && args[0].equals("clear")) {
            Method clear = manager.getClass().getMethod("clearPackagePreferredActivities", String.class);
            clear.setAccessible(true);
            clear.invoke(manager, args[1]);
            System.out.println("Cleared package preferred activities");
        } else {
            throw new IllegalArgumentException("get | clear PACKAGE");
        }
    }
}
