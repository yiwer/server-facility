package cn.code91.facility.web.ratelimit.right;
import cn.code91.facility.web.ratelimit.RateLimit;
import org.springframework.web.bind.annotation.*;
@RestController("rightQuotaEndpoint")
public class QuotaEndpoint {
    @GetMapping("/right-quota") @RateLimit(capacity = 1, permitsPerSecond = 0.001)
    public String execute() { return "ok"; }
}
