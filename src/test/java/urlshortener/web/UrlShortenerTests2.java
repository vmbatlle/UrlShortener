package urlshortener.web;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.stubbing.Answer;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import urlshortener.Application;
import urlshortener.domain.Click;
import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;

import java.net.URI;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static urlshortener.fixtures.ShortURLFixture.someUrl;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("context")
@WebAppConfiguration
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UrlShortenerTests2 {

    private MockMvc mockMvc;

    private static final Integer RATE_GLOBAL_THROTTLING = 15;
    private static final Integer RATE_URI_THROTTLING = 15;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GlobalThrottling globalThrottling;

    @Autowired
    private URIThrotlling uriThrotlling;

    @MockBean
    private ClickService clickService;

    @MockBean
    private ShortURLService shortUrlService;

    @Value("${throttling.tests.run:0}")
    private boolean runThrottlingTests;

    @Value("${throttling.global.get.rate}")
    private Integer globalThrottlingRateGet;

    @Value("${throttling.global.post.rate}")
    private Integer globalThrottlingRatePost;

    @Value("${throttling.uri.rate}")
    private Integer uriThrottlingRate;

    @Before
    public void setup() {
        if (!runThrottlingTests) return;
        this.globalThrottling.setThrottlingGet(globalThrottlingRateGet);
        this.globalThrottling.setThrottlingPost(globalThrottlingRatePost);
        this.uriThrotlling.setThrottling(uriThrottlingRate);
        this.mockMvc = webAppContextSetup(webApplicationContext).build();
        if (globalThrottlingRateGet == null) {
                globalThrottlingRateGet = GlobalThrottling.DEFAULT_RATE_GET;
        }
        if (globalThrottlingRatePost == null) {
                globalThrottlingRatePost = GlobalThrottling.DEFAULT_RATE_POST;
        }
    }

    @Test
    public void thatShortenerExceedsGlobalLimitForRedirection() throws Exception {
        if (!runThrottlingTests) return;
        configureSave(null);
        globalThrottling.setThrottlingGet(RATE_GLOBAL_THROTTLING);
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());

        RequestPostProcessor postProcessor1 = request -> {
                request.setRemoteAddr("192.168.0.1");
                return request;
        };

        RequestPostProcessor postProcessor2 = request -> {
                request.setRemoteAddr("192.168.0.2");
                return request;
            };

        // Sleep initial time
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING) + 3000);

        // 192.168.0.1
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor1))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        
        // Wait less than 60 * 1000 / RATE_GLOBAL_THROTTLING;
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING * 0.5));

        // 192.168.0.2
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor2))
                .andExpect(status().is(429));

        // Sleep needed time
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING));

        // 192.168.0.1
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor1))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));

        // Sleep needed time
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING));

        // 192.168.0.2
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor2))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        
                
        globalThrottling.setThrottlingGet(globalThrottlingRateGet);
    }

    @Test
    public void thatShortenerExceedsGlobalLimitForCreation() throws Exception {
        if (!runThrottlingTests) return;
        configureSave(null);
        globalThrottling.setThrottlingPost(RATE_GLOBAL_THROTTLING);
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());

        RequestPostProcessor postProcessor1 = request -> {
                request.setRemoteAddr("192.168.0.1");
                return request;
        };

        RequestPostProcessor postProcessor2 = request -> {
                request.setRemoteAddr("192.168.0.2");
                return request;
            };

        // Sleep initial time
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING) + 3000);

        // 192.168.0.1
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor1))
                .andExpect(status().isCreated())
                .andExpect(redirectedUrl("http://localhost/f684a3c4"));
        
        // Wait less than 60 * 1000 / RATE_GLOBAL_THROTTLING;
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING * 0.5));

        // 192.168.0.2
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor2))
                .andExpect(status().is(429));

        // Sleep needed time
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING));

        // 192.168.0.1
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor1))
                .andExpect(status().isCreated())
                .andExpect(redirectedUrl("http://localhost/f684a3c4"));

        // Sleep needed time
        Thread.sleep(Math.round(60 * 1000 / RATE_GLOBAL_THROTTLING));

        // 192.168.0.2
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor2))
                .andExpect(redirectedUrl("http://localhost/f684a3c4"))
                .andExpect(status().isCreated());
        
        globalThrottling.setThrottlingPost(globalThrottlingRatePost);
    }

    @Test
    public void thatShortenerExceedsIPLimitForRedirection() throws Exception {
        if (!runThrottlingTests) return;
        configureSave(null);
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());

        RequestPostProcessor postProcessor1 = request -> {
                request.setRemoteAddr("192.168.0.1");
                return request;
        };

        RequestPostProcessor postProcessor2 = request -> {
                request.setRemoteAddr("192.168.0.2");
                return request;
            };

        // sleep 1 minute initial time
        Thread.sleep(60 * 1000 + 1000);

        // 192.168.0.1
        for (int i = 0; i < UrlShortenerController.THROTTLING_GET_LIMIT_PER_ADDR; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor1))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor1))
                .andExpect(status().is(429));

        // 192.168.0.2
        for (int i = 0; i < UrlShortenerController.THROTTLING_GET_LIMIT_PER_ADDR; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor2))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor2))
                .andExpect(status().is(429));

        // sleep 1 minute
        Thread.sleep(60 * 1000 + 1000);

        // 192.168.0.1
        for (int i = 0; i < UrlShortenerController.THROTTLING_GET_LIMIT_PER_ADDR; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor1))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor1))
                .andExpect(status().is(429));

        // 192.168.0.2
        for (int i = 0; i < UrlShortenerController.THROTTLING_GET_LIMIT_PER_ADDR; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor2))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor2))
                .andExpect(status().is(429));

        globalThrottling.setThrottlingPost(globalThrottlingRatePost);
    }


    @Test
    public void thatShortenerExceedsIPLimitForCreation() throws Exception {
        if (!runThrottlingTests) return;
        configureSave(null);

        RequestPostProcessor postProcessor1 = request -> {
                request.setRemoteAddr("192.168.0.1");
                return request;
        };

        RequestPostProcessor postProcessor2 = request -> {
                request.setRemoteAddr("192.168.0.2");
                return request;
            };

        // sleep 1 minute initial time
        Thread.sleep(60 * 1000 + 1000);

        // 192.168.0.1
        for (int i = 0; i < UrlShortenerController.THROTTLING_POST_LIMIT_PER_ADDR; i++) {
                mockMvc.perform(post("/link")
                        .param("url", "http://example.com/")
                        .with(postProcessor1))
                        .andExpect(redirectedUrl("http://localhost/f684a3c4"))
                        .andExpect(status().isCreated());
        }
            
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor1))
                .andExpect(status().is(429));

        // 192.168.0.2
        for (int i = 0; i < UrlShortenerController.THROTTLING_POST_LIMIT_PER_ADDR; i++) {
                mockMvc.perform(post("/link")
                        .param("url", "http://example.com/")
                        .with(postProcessor2))
                        .andExpect(redirectedUrl("http://localhost/f684a3c4"))
                        .andExpect(status().isCreated());
        }
            
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor2))
                .andExpect(status().is(429));

        // sleep 1 minute
        Thread.sleep(60 * 1000 + 1000);

        // 192.168.0.1
        for (int i = 0; i < UrlShortenerController.THROTTLING_POST_LIMIT_PER_ADDR; i++) {
                mockMvc.perform(post("/link")
                        .param("url", "http://example.com/")
                        .with(postProcessor1))
                        .andExpect(redirectedUrl("http://localhost/f684a3c4"))
                        .andExpect(status().isCreated());
                        ;
        }
            
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor1))
                .andExpect(status().is(429));

        // 192.168.0.2
        for (int i = 0; i < UrlShortenerController.THROTTLING_POST_LIMIT_PER_ADDR; i++) {
                mockMvc.perform(post("/link")
                        .param("url", "http://example.com/")
                        .with(postProcessor2))
                        .andExpect(redirectedUrl("http://localhost/f684a3c4"))
                        .andExpect(status().isCreated());
                        ;
        }
            
        mockMvc.perform(post("/link")
                .param("url", "http://example.com/")
                .with(postProcessor2))
                .andExpect(status().is(429));

        globalThrottling.setThrottlingPost(globalThrottlingRatePost);
    }

    @Test
    public void thatShortenerExceedsUriLimitForRedirection() throws Exception {
        if (!runThrottlingTests) return;
        configureSave(null);
        uriThrotlling.setThrottling(RATE_URI_THROTTLING);
        when(shortUrlService.findByKey("anyKeys")).thenReturn(someUrl());

        RequestPostProcessor postProcessor1 = request -> {
                request.setRemoteAddr("192.168.0.1");
                return request;
        };

        RequestPostProcessor postProcessor2 = request -> {
                request.setRemoteAddr("192.168.0.2");
                return request;
            };

        // sleep 1 minute initial time
        Thread.sleep(60 * 1000 + 1000);

        // 192.168.0.1
        mockMvc.perform(get("/{id}", "anyKeys")
                .with(postProcessor1))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        
        // Wait less than 60 * 1000 / RATE_URI_THROTTLING;
        Thread.sleep(Math.round(60 * 1000 / RATE_URI_THROTTLING * 0.5));

        // 192.168.0.2
        mockMvc.perform(get("/{id}", "anyKeys")
                .with(postProcessor2))
                .andExpect(status().is(429));

        // Sleep needed time
        Thread.sleep(Math.round(60 * 1000 / RATE_URI_THROTTLING));

        // 192.168.0.1
        mockMvc.perform(get("/{id}", "anyKeys")
                .with(postProcessor1))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));

        // Sleep needed time
        Thread.sleep(Math.round(60 * 1000 / RATE_URI_THROTTLING));

        // 192.168.0.2
        mockMvc.perform(get("/{id}", "anyKeys")
                .with(postProcessor2))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        
                
        uriThrotlling.setThrottling(uriThrottlingRate);
    }

    private void configureSave(String sponsor) {
        when(shortUrlService.save(any(), any(), any()))
                .then((Answer<ShortURL>) invocation -> new ShortURL(
                        "f684a3c4",
                        "http://example.com/",
                        URI.create("http://localhost/f684a3c4"),
                        sponsor,
                        null,
                        null,
                        0,
                        false,
                        null,
                        null));

        when(clickService.saveClick(any(), any()))
                .then((Answer<Click>) invocation -> new Click((long) 1, null, null, null, null,
                                                null, null, null
                        ));
        
        when(clickService.saveClickUserAgent(any(), any(), any(), any(), any(), any()))
                .then((Answer<Click>) invocation -> new Click((long) 1, null, null, null, null,
                                                null, null, null
                        ));
    }
}