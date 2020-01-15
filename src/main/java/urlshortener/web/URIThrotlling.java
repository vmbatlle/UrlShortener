package urlshortener.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;

/**
 * This class implements the request limitation (throtling) for each short URI.
 */
@Component
public class URIThrotlling {

    /** All throttling units are requests per minute */
    public static final Integer DEFAULT_RATE = 1000; /**< Default limit for URI access **/
    private Integer uriRateLimiterRate; /**< Maximum number of accesses allowed per URI and minute **/
    private Integer uriRateLimiterWarmup; /**< Warup value for URI rate limitter **/

    private final CacheLoader<String, RateLimiter> loader; /**< Loader for @see cache **/
    private final LoadingCache<String, RateLimiter> cache; /**< LRU URI rate limiters */

    /**
     * (constructor)
     * @param rate  the maximum rate for URI access
     * @param warmup  the warmup value for URI rate limitter
     */
    @Autowired
    public URIThrotlling(@Value("${throttling.uri.rate}") final Integer rate,
                         @Value("${throttling.uri.warmup}") final Integer warmup) {
        
        if (rate == null || rate <= 0) {
            uriRateLimiterRate = DEFAULT_RATE;
        } else {
            uriRateLimiterRate = rate;        
        }
        uriRateLimiterWarmup = warmup;

        /**
         * Rate limiter are automatically created and loaded each time a MISS
         * happens while looking up a key into the cache memory.
         * 
         * Type of the {@code RateLimiter} is fixed at construction.
         */
        if (warmup != null && warmup > 0) {
            loader = new CacheLoader<String, RateLimiter>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(uriRateLimiterRate / 60.0, 
                                uriRateLimiterWarmup, TimeUnit.MINUTES);
                }
            };
        } else {
            loader = new CacheLoader<String, RateLimiter>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(uriRateLimiterRate / 60.0);
                }
            };
        }

        cache = CacheBuilder.newBuilder()
                    .maximumSize(100000)
                    .expireAfterAccess(10, TimeUnit.MINUTES)
                    .build(loader);
    }

    /**
     * Set a new rate for URI access. (permits = 0 means disable throttling)
     * @param permitsPerMinute  the number accesses to allow per minute.
     */
    public void setThrottling(final Integer permitsPerMinute) {
        uriRateLimiterRate = permitsPerMinute;
        if (permitsPerMinute < 0) {
            cache.invalidateAll();
        }
    }

    /**
     * Get current rate for URI access.
     * @return the number of URI accesss allowed per minute.
     */
    public Integer getRate() {
        return uriRateLimiterRate;
    }

    /**
     * Check if the current URI access is allowed by rate limitter.
     * @param hash  the unique identifier of the URI
     * @return true iff access is allowed
     */
    public Boolean acquire(final String hash) {
        if (uriRateLimiterRate <= 0) return true;
        RateLimiter rateLimiter;
        try {
            rateLimiter = cache.get(hash);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        }
        return rateLimiter.tryAcquire();
    }
}