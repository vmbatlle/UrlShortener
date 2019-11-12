package urlshortener.web;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;

import javax.servlet.http.HttpServletRequest;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

import com.weddini.throttling.Throttling;
import com.weddini.throttling.ThrottlingType;
import java.util.concurrent.TimeUnit;

@RestController
public class UrlShortenerController {
    private final ShortURLService shortUrlService;

    private final ClickService clickService;

    public static final int THROTTLING_GET_LIMIT = 10;
    public static final int THROTTLING_POST_LIMIT = 10;

    public UrlShortenerController(ShortURLService shortUrlService, ClickService clickService) {
        this.shortUrlService = shortUrlService;
        this.clickService = clickService;
    }

    private boolean isAccesible(String url_s) {
        int responseCode = 400;
        try {
            URL url = new URL(url_s);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("HEAD");
            responseCode = huc.getResponseCode();
        } catch (MalformedURLException e1) {
            //e1.printStackTrace();
        } catch (IOException e) {
            //e.printStackTrace();
        }

        return responseCode == HttpURLConnection.HTTP_OK;
    }

    @RequestMapping(value = "/{id:(?!link|index).*}", method = RequestMethod.GET)
    @Throttling(type = ThrottlingType.RemoteAddr, limit = THROTTLING_GET_LIMIT, timeUnit = TimeUnit.MINUTES)
    public ResponseEntity<?> redirectTo(@PathVariable String id, HttpServletRequest request) {
        ShortURL l = shortUrlService.findByKey(id);
        if (l != null) {
            List<String> data = null;
            try {
                data = extractInfoUserAgent(request);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(data != null){
                clickService.saveClick2(id, extractIP(request), data.get(0), data.get(1), data.get(2), request.getHeader("referer"));
            }
            else{
                clickService.saveClick(id, extractIP(request));
            }
            return createSuccessfulRedirectToResponse(l);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public String all() {
        return clickService.clicksRecived();
    }
    
    @RequestMapping(value = "/download-data", method = RequestMethod.GET)
    public ResponseEntity<byte[]> download_all() {
        String json = clickService.clicksRecived();
        String fileName = "clicks.json";
        byte[] isr = json.getBytes();
        HttpHeaders respHeaders = new HttpHeaders();
		respHeaders.setContentLength(isr.length);
		respHeaders.setContentType(new MediaType("text", "json"));
		respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		respHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
		return new ResponseEntity<byte[]>(isr, respHeaders, HttpStatus.OK);
    }

    @RequestMapping(value = "/link", method = RequestMethod.POST)
    @Throttling(type = ThrottlingType.RemoteAddr, limit = THROTTLING_POST_LIMIT, timeUnit = TimeUnit.MINUTES)
    public ResponseEntity<ShortURL> shortener(@RequestParam("url") String url,
                                              @RequestParam(value = "sponsor", required = false) String sponsor,
                                              HttpServletRequest request) {
        UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
        if (urlValidator.isValid(url) && isAccesible(url)) {
            if (sponsor != null && sponsor.equals("")) sponsor = null;
            if (sponsor != null && shortUrlService.findByKey(sponsor) != null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            ShortURL su = shortUrlService.save(url, sponsor, request.getRemoteAddr());                                               
            HttpHeaders h = new HttpHeaders();
            h.setLocation(su.getUri());
            return new ResponseEntity<>(su, h, HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }


    private String extractIP(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private List<String> extractInfoUserAgent(HttpServletRequest request) throws MalformedURLException, IOException {
        String url = "http://api.userstack.com/detect?";
        // String charset = "UTF-8"; // Or in Java 7 and later, use the constant:
        // java.nio.charset.StandardCharsets.UTF_8.name()
        String param1 = request.getHeader("User-Agent");
        String param2 = "e4edfb3090e960cd96d7a9df73acc622"; // API-KEY

        url = url + "access_key=e4edfb3090e960cd96d7a9df73acc622&ua=" + param1;
        URL uri = new URL(url.replace("\"", "%22").replace(" ",  "%20"));
        HttpURLConnection connection = (HttpURLConnection) uri.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        int responseCode = connection.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        // print in String
        System.out.println(response.toString());
        
        // Read JSON response and print
        try {
            JSONObject myResponse;
            List<String> data = new ArrayList<>();
            myResponse = new JSONObject(response.toString());
            data.add(myResponse.getJSONObject("os").getString("name"));
            data.add(myResponse.getJSONObject("device").getString("type"));
            data.add(myResponse.getJSONObject("browser").getString("name"));
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ResponseEntity<?> createSuccessfulRedirectToResponse(ShortURL l) {
        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create(l.getTarget()));
        return new ResponseEntity<>(h, HttpStatus.valueOf(l.getMode()));
    }
/*
    @ResponseStatus(code = HttpStatus.TOO_MANY_REQUESTS, reason = "Too many requests")
    public class ThrottlingException extends RuntimeException {
    }
*/
}
