package cn.code91.facility.web.ratelimit.left;
import cn.code91.facility.web.ratelimit.RateLimit;
import org.springframework.web.bind.annotation.*;
@RestController("leftQuotaEndpoint")
public class QuotaEndpoint {
    @GetMapping("/left-quota") @RateLimit(capacity = 1, permitsPerSecond = 0.001)
    public String execute() { return "ok"; }
}
