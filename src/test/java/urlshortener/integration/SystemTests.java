package urlshortener.integration;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SystemTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    public void testHome() {
        ResponseEntity<String> entity = restTemplate.getForEntity("/", String.class);
        assertThat(entity.getStatusCode(), is(HttpStatus.OK));
        assertTrue(entity.getHeaders().getContentType().isCompatibleWith(new MediaType("text", "html")));
        assertThat(entity.getBody(), containsString("<title>URL"));
    }

    @Test
    public void testCss() {
        ResponseEntity<String> entity = restTemplate.getForEntity("/webjars/bootstrap/3.3.5/css/bootstrap.min.css", String.class);
        assertThat(entity.getStatusCode(), is(HttpStatus.OK));
        assertThat(entity.getHeaders().getContentType(), is(MediaType.valueOf("text/css")));
        assertThat(entity.getBody(), containsString("body"));
    }

    @Test
    public void testCreateLink() throws Exception {
        ResponseEntity<String> entity = postLink("http://example.com/");

        assertThat(entity.getStatusCode(), is(HttpStatus.CREATED));
        assertThat(entity.getHeaders().getLocation(), is(new URI("http://localhost:" + this.port + "/f684a3c4")));
        assertThat(entity.getHeaders().getContentType(), is(new MediaType("application", "json", StandardCharsets.UTF_8)));
        ReadContext rc = JsonPath.parse(entity.getBody());
        assertThat(rc.read("$.hash"), is("f684a3c4"));
        assertThat(rc.read("$.uri"), is("http://localhost:" + this.port + "/f684a3c4"));
        assertThat(rc.read("$.target"), is("http://example.com/"));
        assertThat(rc.read("$.sponsor"), is(nullValue()));
    }

    @Test
    public void testRedirection() throws Exception {
        postLink("http://example.com/");

        ResponseEntity<String> entity = restTemplate.getForEntity("/f684a3c4", String.class);
        assertThat(entity.getStatusCode(), is(HttpStatus.TEMPORARY_REDIRECT));
        assertThat(entity.getHeaders().getLocation(), is(new URI("http://example.com/")));
    }

    @Test
    public void testCreateLinkSponsor() throws Exception {
        ResponseEntity<String> entity = postLink("http://example.com/","sponsor");

        assertThat(entity.getStatusCode(), is(HttpStatus.CREATED));
        assertThat(entity.getHeaders().getLocation(), is(new URI("http://localhost:" + this.port + "/sponsor")));
        assertThat(entity.getHeaders().getContentType(), is(new MediaType("application", "json", StandardCharsets.UTF_8)));
        ReadContext rc = JsonPath.parse(entity.getBody());
        assertThat(rc.read("$.hash"), is("sponsor"));
        assertThat(rc.read("$.uri"), is("http://localhost:" + this.port + "/sponsor"));
        assertThat(rc.read("$.target"), is("http://example.com/"));
        assertThat(rc.read("$.sponsor"), is("sponsor"));
    }

    @Test
    public void AddExistingURIAfter301ToRedirect() throws Exception {
        ResponseEntity<String> entity = postLink("https://bit.ly/2sgNe8D", "google");
        assertThat(entity.getStatusCode(), is(HttpStatus.CREATED));

        ResponseEntity<String> entity2 = restTemplate.getForEntity("/google", String.class);
        assertThat(entity2.getHeaders().getLocation(), is(new URI("https://www.google.com/")));
    }

    @Test
    public void DeleteDisabledUrisAfterTime() throws Exception {
        ResponseEntity<String> entity = postLink("http://localhost:" + this.port + "/test_scheduler");
        assertThat(entity.getStatusCode(), is(HttpStatus.CREATED));
        ReadContext rc = JsonPath.parse(entity.getBody());
        String hash = rc.read("$.hash");

        //set to bad request
        restTemplate.getForEntity("/test_scheduler", String.class);

        Thread.sleep(11000);

        ResponseEntity<String> entity2 = restTemplate.getForEntity("/"+ hash, String.class);
        assertThat(entity2.getStatusCode(), is(HttpStatus.NOT_ACCEPTABLE));

        Thread.sleep(11000);

        ResponseEntity<String> entity3 = restTemplate.getForEntity("/"+ hash, String.class);
        assertThat(entity3.getStatusCode(), is(HttpStatus.TEMPORARY_REDIRECT));
    }

    @Test
    public void TestRedirectWithPathAndQUery() throws Exception {
        ResponseEntity<String> entity = postLink("http://example.com");
        assertThat(entity.getStatusCode(), is(HttpStatus.CREATED));
        ReadContext rc = JsonPath.parse(entity.getBody());
        String hash = rc.read("$.hash");

        ResponseEntity<String> entity2 = restTemplate.getForEntity("http://localhost:" + this.port + "/" + hash + "/path?query={val}", String.class, "1");
        assertThat(entity2.getStatusCode(), is(HttpStatus.TEMPORARY_REDIRECT));
        assertThat(entity2.getHeaders().getLocation(), is(new URI("http://example.com/path?query=1")));
        assertThat(entity2.getHeaders().get("query").get(0), is("1"));

    }


    @Test
    public void testRedirectionSponsor() throws Exception {
        postLink("http://example.com/","sponsor");

        ResponseEntity<String> entity = restTemplate.getForEntity("/sponsor", String.class);
        assertThat(entity.getStatusCode(), is(HttpStatus.TEMPORARY_REDIRECT));
        assertThat(entity.getHeaders().getLocation(), is(new URI("http://example.com/")));
    }

    private ResponseEntity<String> postLink(String url) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("url", url);
        return restTemplate.postForEntity("/link", parts, String.class);
    }

    private ResponseEntity<String> postLink(String url, String sponsor) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("url", url);
        if (sponsor != null) parts.add("sponsor", sponsor);
        return restTemplate.postForEntity("/link", parts, String.class);
    }

}