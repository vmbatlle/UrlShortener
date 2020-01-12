package urlshortener.web;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;

import urlshortener.Application;
import urlshortener.domain.Click;
import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;

import java.net.URI;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Size;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static urlshortener.fixtures.ShortURLFixture.someUrl;

import static org.hamcrest.Matchers.*;

public class UrlShortenerTests4 {

    private long clicks;

    private MockMvc mockMvc;

    @Mock
    private ClickService clickService;

    @Mock
    private ShortURLService shortUrlService;

    @Mock
    private URIThrotlling uriThrotlling;

    @Mock
    private APIAccess api_acces;

    @Mock
    private GlobalThrottling globalThrottling;

    @InjectMocks
    private UrlShortenerController urlShortener;

    @Before
    public void setup() {
        this.clicks = 0;
        MockitoAnnotations.initMocks(this);
        this.mockMvc = MockMvcBuilders.standaloneSetup(urlShortener).build();
        ReflectionTestUtils.setField(api_acces, "userStackKey", "e4edfb3090e960cd96d7a9df73acc622");
        when(globalThrottling.acquireGet()).then(invocation -> true);
        when(globalThrottling.acquirePost()).then(invocation -> true);
        when(uriThrotlling.acquire(any())).then(invocation -> true);
    }

    @Test
    public void thatCollectEmptyClicks() throws Exception {
        configureSave(null);

        mockMvc.perform(MockMvcRequestBuilders.get("/all")).andDo(print())
                .andExpect(model().attribute("clicks", clickService.allClicks()))
                .andExpect(model().attribute("pages", 0));
    }
    
    @Test
    public void thatCollectAll()
            throws Exception {
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());
        configureSave(null);

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        
        
        mockMvc.perform(MockMvcRequestBuilders.get("/all")).andDo(print())
                .andExpect(model().attribute("clicks",  hasSize(2)));
    }

    @Test
    public void thatCollectAllAndHaveOBFields()
            throws Exception {
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());
        configureSave(null);

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(MockMvcRequestBuilders.get("/all")).andDo(print())
                .andExpect(model().attribute("clicks",  hasItem(
                    allOf(
                        hasProperty("ip",is("null")),
                        hasProperty("created", is(new Timestamp((long) 1)))
                        ))));
    }
    
    @Test
    public void thatCollectPageSize()
            throws Exception {
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());
        configureSave(null);

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(MockMvcRequestBuilders.get("/all?size=2")).andDo(print())
                .andExpect(model().attribute("clicks",  hasSize(2)));
    }
    
    @Test
    public void thatCollectPageSizeAndOffset()
            throws Exception {
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());
        
        when(clickService.clicksReceived((long) 2,(long) 2)).thenReturn(someList());
        configureSave(null);

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(MockMvcRequestBuilders.get("/all").param("page", "2").param("size", "2"))
                .andDo(print())
                .andExpect(model().attribute("clicks",  hasSize(1)));
    }

    @Test
    public void thatCollectPageSizeAndOffsetOutOfRange()
            throws Exception {
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());
        
        configureSave(null);

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(MockMvcRequestBuilders.get("/all").param("page", "10").param("size", "5"))
                .andDo(print())
                .andExpect(model().attribute("clicks",  hasSize(0)));
    }

    @Test
    public void thatTestThePerformance()
            throws Exception {
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());
        
        configureSave(null);

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(MockMvcRequestBuilders.get("/all").param("page", "10").param("size", "5"))
                .andDo(print())
                .andExpect(model().attribute("clicks",  hasSize(0)));
    }

    @Test
    public void thatCollectAllAndHaveOPFields()
            throws Exception {
        when(shortUrlService.findByKey("someKey")).thenReturn(someUrl());
        when(clickService.count()).thenReturn((long) 2);
        configureSave(null);

        mockMvc.perform(get("/{id}", "someKey"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://example.com/"));
        this.clicks = this.clicks + 1;
        
        mockMvc.perform(MockMvcRequestBuilders.get("/all")).andDo(print())
                .andExpect(model().attribute("clicks",  hasItem(
                    allOf(
                        hasProperty("platform",is("null")),
                        hasProperty("browser",is("null")),
                        hasProperty("referrer",is("null"))
                        ))));
    }

    public static List<Click> someList(){
        List<Click> lc = new ArrayList<>();
        lc.add(new Click((long) 0, null, null, null, null, null, null, null));
        return lc;
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
        .then((Answer<Click>) invocation -> new Click(clicks, null, new Timestamp((long) 1), "null", "null",
                                        "null", "null", "null"
                ));
                
        when(clickService.saveClickUserAgent(any(), any(), any(), any(), any(), any()))
                .then((Answer<Click>) invocation -> new Click(clicks, null, new Timestamp((long) 1), "null", "null",
                                                "null", "null", "null"
                        ));

        when(globalThrottling.acquireGet()).then(invocation -> true);
        when(globalThrottling.acquirePost()).then(invocation -> true);
        when(uriThrotlling.acquire(any())).then(invocation -> true);
    }
}
