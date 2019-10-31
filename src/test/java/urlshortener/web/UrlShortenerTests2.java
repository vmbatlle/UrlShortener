package urlshortener.web;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assume;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.stubbing.Answer;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import urlshortener.Application;
import urlshortener.domain.ShortURL;
import urlshortener.service.ShortURLService;

import java.nio.charset.Charset;
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
public class UrlShortenerTests2 {

    private final MediaType jsonContentType = new MediaType(
            MediaType.APPLICATION_JSON.getType(),
            MediaType.APPLICATION_JSON.getSubtype(),
            Charset.forName("utf8")
    );

    private final MediaType textPlainContentType = new MediaType(
            MediaType.TEXT_PLAIN.getType(),
            MediaType.TEXT_PLAIN.getSubtype(),
            Charset.forName("utf8")
    );

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UrlShortenerController urlShortener;

    @MockBean
    private ShortURLService shortUrlService;

    @Value("${throttling.tests.run:0}")
    private boolean runThrottlingTests;

    @Before
    public void setup() {
        if (!runThrottlingTests) return;
        this.mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void thatShortenerExceedsIPLimitForRedirection() throws Exception {
        if (!runThrottlingTests) return;
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());

        RequestPostProcessor postProcessor1 = request -> {
                request.setRemoteAddr("192.168.0.1");
                return request;
        };

        RequestPostProcessor postProcessor2 = request -> {
                request.setRemoteAddr("192.168.0.2");
                return request;
            };

        // 192.168.0.1
        for (int i = 0; i < urlShortener.THROTTLING_GET_LIMIT; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor1))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor1))
                .andExpect(status().is(429));

        // 192.168.0.2
        for (int i = 0; i < urlShortener.THROTTLING_GET_LIMIT; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor2))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor2))
                .andExpect(status().is(429));

        // sleep 1 minute
        Thread.sleep(60 * 1000 + 100);

        // 192.168.0.1
        for (int i = 0; i < urlShortener.THROTTLING_GET_LIMIT; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor1))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor1))
                .andExpect(status().is(429));

        // 192.168.0.2
        for (int i = 0; i < urlShortener.THROTTLING_GET_LIMIT; i++) {
            mockMvc.perform(get("/{id}", "someKey")
                    .with(postProcessor2))
                    .andExpect(status().isTemporaryRedirect())
                    .andExpect(redirectedUrl("http://example.com/"));
        }
            
        mockMvc.perform(get("/{id}", "someKey")
                .with(postProcessor2))
                .andExpect(status().is(429));
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

        // 192.168.0.1
        for (int i = 0; i < urlShortener.THROTTLING_POST_LIMIT; i++) {
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
        for (int i = 0; i < urlShortener.THROTTLING_POST_LIMIT; i++) {
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
        Thread.sleep(60 * 1000 + 100);

        // 192.168.0.1
        for (int i = 0; i < urlShortener.THROTTLING_POST_LIMIT; i++) {
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
        for (int i = 0; i < urlShortener.THROTTLING_POST_LIMIT; i++) {
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
    }
}