import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/** Literal consumer examples, specified independently of cleaner output. */
public final class PolicySamples {
    public record Sample(String name, String html, String expected) {}
    public static final Sample[] BASIC = {
        new Sample("bold", "<b>bold</b>", "<b>bold</b>"),
        new Sample("unicode", "你好 👋", "你好 👋"),
        new Sample("text-angle", "2 &lt; 3 &amp; 5 &gt; 4", "2 &lt; 3 &amp; 5 &gt; 4"),
        new Sample("script", "<script>PRIVATE()</script><b>ok</b>", "<b>ok</b>"),
        new Sample("event", "<b onclick='PRIVATE()'>ok</b>", "<b>ok</b>"),
        new Sample("style", "<b style='background:url(javascript:PRIVATE())'>ok</b>", "<b>ok</b>"),
        new Sample("javascript", "<a href='javascript:PRIVATE()'>go</a>", "<a rel=\"nofollow\">go</a>"),
        new Sample("encoded-scheme", "<a href='java&#x73;cript:PRIVATE()'>go</a>", "<a rel=\"nofollow\">go</a>"),
        new Sample("mixed-case", "<A HREF='JaVaScRiPt:PRIVATE()'>go</A>", "<a rel=\"nofollow\">go</a>"),
        new Sample("data-uri", "<a href='data:text/html,PRIVATE'>go</a>", "<a rel=\"nofollow\">go</a>"),
        new Sample("relative-uri", "<a href='/account'>go</a>", "<a rel=\"nofollow\">go</a>"),
        new Sample("https-uri", "<a href='https://example.com/account'>go</a>", "<a href=\"https://example.com/account\" rel=\"nofollow\">go</a>"),
        new Sample("hostless-http", "<a href='http://'>go</a>", "<a rel=\"nofollow\">go</a>"),
        new Sample("malformed-nesting", "<b><i>x</b>y</i>", "<b><i>x</i></b><i>y</i>"),
        new Sample("comment", "before<!-- PRIVATE -->after", "beforeafter"),
        new Sample("iframe", "<iframe src='https://example.com'>PRIVATE</iframe><b>ok</b>", "<b>ok</b>")
    };

    public static void main(String[] args) {
        boolean observe = args.length == 1 && args[0].equals("observe");
        for (var sample : BASIC) {
            String actual = Jsoup.clean(sample.html(), Safelist.basic());
            System.out.println(sample.name() + "\t" + actual.replace("\n", "\\n"));
            if (!observe && !actual.equals(sample.expected()))
                throw new AssertionError("Policy sample mismatch: " + sample.name());
        }
        System.out.println("HTML_POLICY_SAMPLES_" + (observe ? "OBSERVED" : "PASS"));
    }
}
