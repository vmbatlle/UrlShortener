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

@Component
public class URIThrotlling {

    /** All throttling units are requests per minute */
    public static final Integer DEFAULT_RATE = 1000;
    private Integer uriRateLimiterRate;
    private Integer uriRateLimiterWarmup;

    private final CacheLoader<String, RateLimiter> loader;
    private final LoadingCache<String, RateLimiter> cache;

    @Autowired
    public URIThrotlling(@Value("${throttling.uri.rate}") final Integer rate,
                         @Value("${throttling.uri.warmup}") final Integer warmup) {
        
        if (rate == null || rate <= 0) {
            uriRateLimiterRate = DEFAULT_RATE;
        } else {
            uriRateLimiterRate = rate;        
        }
        uriRateLimiterWarmup = warmup;

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

    public void setThrottling(final Integer permitsPerMinute) {
        uriRateLimiterRate = permitsPerMinute;
        if (permitsPerMinute < 0) {
            cache.invalidateAll();
        }
    }

    public Integer getRate() {
        return uriRateLimiterRate;
    }

    public Boolean acquire(final String hash) {
        if (uriRateLimiterRate <= 0) return true;
        RateLimiter rateLimiter;
        try {
            rateLimiter = cache.get(hash);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        }
        System.err.println(hash);
        return rateLimiter.tryAcquire();
    }
}