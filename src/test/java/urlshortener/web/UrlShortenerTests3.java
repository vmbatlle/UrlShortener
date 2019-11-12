package urlshortener.web;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.net.URI;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.hamcrest.Matchers.nullValue;
import static urlshortener.fixtures.ShortURLFixture.someUrl;

import urlshortener.Application;
import urlshortener.domain.ShortURL;
import urlshortener.service.ShortURLService;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("context")
@WebAppConfiguration
public class UrlShortenerTests3 {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private ShortURLService shortUrlService;


    @Before
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void AddNewSponsoredWorkingURI() throws Exception {
        configureSave("sponsor");

        mockMvc.perform(post("/link").param("url", "http://example.com/")
                                     .param("sponsor", "sponsor"))
        .andDo(print())
        .andExpect(redirectedUrl("http://localhost/sponsor"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.hash", is("sponsor")))
        .andExpect(jsonPath("$.uri", is("http://localhost/sponsor")))
        .andExpect(jsonPath("$.target", is("http://example.com/")))
        .andExpect(jsonPath("$.sponsor", is("sponsor")));
    }

    @Test
    public void AddExistingSponsoredURI() throws Exception {

        when(shortUrlService.findByKey("sponsor")).thenReturn(someUrl());

        mockMvc.perform(post("/link").param("url", "http://example.com/")
                                     .param("sponsor", "sponsor"))
        .andDo(print())
        .andExpect(status().isBadRequest());
    }

    @Test
    public void AddReachableURI() throws Exception {
        configureSave(null);

        mockMvc.perform(post("/link").param("url", "http://example.com/"))
                .andDo(print())
                .andExpect(redirectedUrl("http://localhost/f684a3c4"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hash", is("f684a3c4")))
                .andExpect(jsonPath("$.uri", is("http://localhost/f684a3c4")))
                .andExpect(jsonPath("$.target", is("http://example.com/")))
                .andExpect(jsonPath("$.sponsor", is(nullValue())));
    }

    @Test
    public void AddNotReachableURI() throws Exception {
        configureSave(null);

        mockMvc.perform(post("/link").param("url", "http://notawebpage.com/"))
        .andDo(print())
        .andExpect(status().isBadRequest());
    }

    private void configureSave(String sponsor) {
        if (sponsor != null) {
            when(shortUrlService.save(any(), any(), any()))
                    .then((Answer<ShortURL>) invocation -> new ShortURL(
                            sponsor,
                            "http://example.com/",
                            URI.create("http://localhost/sponsor"),
                            sponsor,
                            null,
                            null,
                            0,
                            false,
                            null,
                            null));
        }
        else {

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

}