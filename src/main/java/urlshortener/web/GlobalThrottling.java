package urlshortener.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.RateLimiter;

@Component
public class GlobalThrottling {

    /** All throttling units are requests per minute */
    public static final Integer DEFAULT_RATE_GET = 10000;
    public static final Integer DEFAULT_RATE_POST = 10000;
    private Boolean globalRateLimiterGetActive;
    private Boolean globalRateLimiterPostActive;
    private RateLimiter globalRateLimiterGet;
    private RateLimiter globalRateLimiterPost;

    @Autowired
    public GlobalThrottling(@Value("${throttling.global.get.rate}") Integer rateGet, 
                            @Value("${throttling.global.post.rate}") Integer ratePost,
                            @Value("${throttling.global.get.warmup}") final Integer warmupGet,
                            @Value("${throttling.global.post.warmup}") final Integer warmupPost) {
        
        globalRateLimiterGetActive = (rateGet == null || rateGet > 0);
        globalRateLimiterPostActive = (ratePost == null || ratePost > 0);
        if (rateGet == null || rateGet <= 0) rateGet = DEFAULT_RATE_GET;
        if (ratePost == null || ratePost <= 0) ratePost = DEFAULT_RATE_POST;

        if (warmupGet != null) {
            globalRateLimiterGet = RateLimiter.create(rateGet / 60.0, warmupGet, TimeUnit.MINUTES);
        } else {
            globalRateLimiterGet = RateLimiter.create(rateGet / 60.0);
        }
        if (warmupPost != null) {
            globalRateLimiterPost = RateLimiter.create(ratePost / 60.0, warmupPost, TimeUnit.MINUTES);
        } else {
            globalRateLimiterPost = RateLimiter.create(ratePost / 60.0);
        }
    }

    public void setThrottlingGet(final Integer permitsPerMinute) {
        globalRateLimiterGetActive = (permitsPerMinute > 0);
        if (permitsPerMinute > 0) {
            globalRateLimiterGet.setRate(permitsPerMinute / 60.0);
        }
    }

    public Integer getRateGet() {
        return (int)Math.round(globalRateLimiterGet.getRate() * 60.0);
    }

    public void setThrottlingPost(final Integer permitsPerMinute) {
        globalRateLimiterPostActive = (permitsPerMinute > 0);
        if (permitsPerMinute > 0) {
            globalRateLimiterPost.setRate(permitsPerMinute / 60.0);
        }
    }

    public Integer getRatePost() {
        return (int)Math.round(globalRateLimiterPost.getRate() * 60.0);
    }

    public Boolean acquireGet() {
        return !globalRateLimiterGetActive || globalRateLimiterGet.tryAcquire();
    }

    public Boolean acquirePost() {
        return !globalRateLimiterPostActive || globalRateLimiterPost.tryAcquire();
    }
}