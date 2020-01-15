package urlshortener.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.RateLimiter;

/**
 * This class implements the request limitation (throtling) for the entire app.
 */
@Component
public class GlobalThrottling {

    /** All throttling units are requests per minute */
    public static final Integer DEFAULT_RATE_GET = 100000; /**< Default limit for global GET requests **/
    public static final Integer DEFAULT_RATE_POST = 100000; /**< Default limit for global POST requests **/
    private Boolean globalRateLimiterGetActive; /**< Status of rate limitter for global GET requests **/
    private Boolean globalRateLimiterPostActive; /**< Status of rate limitter for global POST requests **/
    private RateLimiter globalRateLimiterGet; /**< Rate limitter for global GET requests **/
    private RateLimiter globalRateLimiterPost; /**< Rate limitter for global POST requests **/

    /**
     * (constructor)
     * @param rateGet  the maximum rate for GET requests
     * @param ratePost  the maximum rate for POST requests
     * @param warmupGet  the warmup value for GET rate limitter
     * @param warmupPost  the warup value for POST rate limitter
     */
    @Autowired
    public GlobalThrottling(@Value("${throttling.global.get.rate}") Integer rateGet, 
                            @Value("${throttling.global.post.rate}") Integer ratePost,
                            @Value("${throttling.global.get.warmup}") final Integer warmupGet,
                            @Value("${throttling.global.post.warmup}") final Integer warmupPost) {
        
        globalRateLimiterGetActive = (rateGet == null || rateGet > 0);
        globalRateLimiterPostActive = (ratePost == null || ratePost > 0);
        if (rateGet == null || rateGet <= 0) rateGet = DEFAULT_RATE_GET;
        if (ratePost == null || ratePost <= 0) ratePost = DEFAULT_RATE_POST;

        /** Rate limitters are created once, an its type cannot be changed */
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

    /**
     * Set a new rate for GET requests. (permits = 0 means disable throttling)
     * @param permitsPerMinute  the number of GET requests to allow per minute.
     */
    public void setThrottlingGet(final Integer permitsPerMinute) {
        globalRateLimiterGetActive = (permitsPerMinute > 0);
        if (permitsPerMinute > 0) {
            globalRateLimiterGet.setRate(permitsPerMinute / 60.0);
        }
    }

    /**
     * Get current rate for GET requests.
     * @return the number of GET requests to allow per minute.
     */
    public Integer getRateGet() {
        return (int)Math.round(globalRateLimiterGet.getRate() * 60.0);
    }

    /**
     * Set a new rate for POST requests. (permits = 0 means disable throttling)
     * @param permitsPerMinute  the number of POST requests to allow per minute.
     */
    public void setThrottlingPost(final Integer permitsPerMinute) {
        globalRateLimiterPostActive = (permitsPerMinute > 0);
        if (permitsPerMinute > 0) {
            globalRateLimiterPost.setRate(permitsPerMinute / 60.0);
        }
    }

    /**
     * Get current rate for POST requests.
     * @return the number of POST requests to allow per minute.
     */
    public Integer getRatePost() {
        return (int)Math.round(globalRateLimiterPost.getRate() * 60.0);
    }

    /**
     * Check if the current GET request is allowed by global rate limitter.
     * @return true iff allowed.
     */
    public Boolean acquireGet() {
        return !globalRateLimiterGetActive || globalRateLimiterGet.tryAcquire();
    }

    /**
     * Check if the current POST request is allowed by global rate limitter.
     * @return true iff allowed.
     */
    public Boolean acquirePost() {
        return !globalRateLimiterPostActive || globalRateLimiterPost.tryAcquire();
    }
}