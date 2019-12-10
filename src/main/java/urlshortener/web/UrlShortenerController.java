package urlshortener.web;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;

import javax.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

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
        boolean ret = false;
        try {
            // ret = InetAddress.getByName(new URL(url_s).getHost()).isReachable(1000);
            // int responseCode = 400;
            // URL url = new URL(url_s);
            // HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            // huc.setRequestMethod("HEAD");
            // huc.setConnectTimeout(1000);
            // huc.setReadTimeout(1000);
            // responseCode = huc.getResponseCode();
            // ret = ret && ( responseCode == HttpURLConnection.HTTP_OK);
            
            int responseCode = 400;
            Response response = Jsoup.connect(url_s).timeout(1000).userAgent("Mozilla").execute();
            responseCode = response.statusCode();
            System.out.println("STCODE: " + responseCode);
            ret = responseCode == HttpURLConnection.HTTP_OK;

        } catch (UnknownHostException e2) {
            e2.printStackTrace();
            ret = false;
        } catch (IOException e2) {
            e2.printStackTrace();
            ret = false;
        }
        return ret;
    }

@RequestMapping(value = {"/{id:(?!link|index).*}","/{id:(?!link|index|webjars|js|bootstrap)[a-z0-9]*}/**"}, method = RequestMethod.GET)
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
            Map<String, String[]> params_map = request.getParameterMap();
            Set<String> params_keys = params_map.keySet();

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            for (String key : params_keys) { System.out.println(key); params.addAll(key, Arrays.asList(params_map.get(key))); }
            String restOfTheUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            //String restOfTheUrl = (String) request.getRequestURI();

            if (restOfTheUrl != null && restOfTheUrl.length() > 9) {
                restOfTheUrl = restOfTheUrl.substring(restOfTheUrl.indexOf("/",2));
                //System.out.println(restOfTheUrl);
            } else {
                restOfTheUrl = "";
            }
            //if (path != null) System.out.println(path);
            return createSuccessfulRedirectToResponse(l,restOfTheUrl,params);
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

        url = url + "access_key=" + param2 + "&ua=" + param1;
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

    private ResponseEntity<?> createSuccessfulRedirectToResponse(ShortURL l, String restURL, MultiValueMap<String,String> params) {
        HttpHeaders h = new HttpHeaders();
        String querys = "";
        if (!params.isEmpty()) {
            querys = "?";
            int i = params.keySet().size();
            for (String s : params.keySet()) {
                querys += s + "=" + params.getFirst(s);
                i--;
                if (i != 0) querys += "&";
            }
        }
        h.setLocation(URI.create(l.getTarget() + restURL + querys));
        h.addAll(params);
        return new ResponseEntity<>(h, HttpStatus.valueOf(l.getMode()));
    }
}
