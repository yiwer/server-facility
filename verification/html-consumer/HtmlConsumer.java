import cn.code91.facility.web.util.XssUtil;
import cn.code91.facility.web.util.XssLevel;
import java.util.Random;

/** Ordinary jar consumer: no Spring, Servlet or test framework runtime. */
public final class HtmlConsumer {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("absent")) {
            try {
                XssUtil.clean("<b>ok</b>");
                throw new AssertionError("Missing explicitly selected jsoup must not silently accept raw HTML");
            } catch (NoClassDefFoundError expected) {
                System.out.println("HTML_CONSUMER_ABSENT_PASS explicitDependency=true");
                return;
            }
        }
        for (String absent : new String[]{"org.springframework.context.ApplicationContext", "jakarta.servlet.Servlet", "org.junit.jupiter.api.Test"}) {
            try { Class.forName(absent); throw new AssertionError("Unexpected framework runtime"); }
            catch (ClassNotFoundException expected) { }
        }
        PolicySamples.main(new String[0]);
        for (var sample : PolicySamples.BASIC) equal(sample.expected(), XssUtil.clean(sample.html()));
        for (int size : new int[]{262143, 262144}) equal("x".repeat(size), XssUtil.clean("x".repeat(size)));
        rejected("x".repeat(262145));
        String nested = "<div>".repeat(10000) + "text" + "</div>".repeat(10000);
        equal("text", XssUtil.clean(nested));
        Random random = new Random(320025);
        for (int i = 0; i < 512; i++) {
            String scheme = switch (random.nextInt(4)) {
                case 0 -> "javascript:";
                case 1 -> "java&#x73;cript:";
                case 2 -> "JaVaScRiPt:";
                default -> "data:text/html,";
            };
            equal("<a rel=\"nofollow\">go</a>", XssUtil.clean("<a href='" + scheme + "PRIVATE()' onclick='PRIVATE()'>go</a>"));
        }
        String fragment = "<b onclick='PRIVATE()'>你好 👋</b><script>PRIVATE()</script>";
        for (int i = 0; i < 1000; i++) XssUtil.clean(fragment);
        long baseline = retained(), maximum = 0;
        for (int cycle = 0; cycle < 5; cycle++) {
            for (int i = 0; i < 2000; i++) {
                equal("<b>你好 👋</b>", XssUtil.clean(fragment));
                rejected("x".repeat(262145));
            }
            long current = retained();
            maximum = Math.max(maximum, current);
            if (current > baseline + 8L * 1024 * 1024 || current > 48L * 1024 * 1024)
                throw new AssertionError("Retained heap exceeded registered observation budget");
        }
        System.out.println("HTML_CONSUMER_PASS samples=16 seed=320025 fuzz=512 depth=10000 cycles=5 successful=10000 rejected=10000 retainedBaseline="
                + baseline + " retainedMaximum=" + maximum);
    }
    private static void rejected(String html) {
        try { XssUtil.clean(html); throw new AssertionError("HTML budget did not reject"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("HTML contract mismatch");
    }
    private static long retained() {
        System.gc();
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }
}
