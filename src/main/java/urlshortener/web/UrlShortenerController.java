package urlshortener.web;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import urlshortener.domain.Click;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;


import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;
import urlshortener.service.UrlChecker;

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

import java.util.Optional;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

import com.weddini.throttling.Throttling;
import com.weddini.throttling.ThrottlingType;
import java.util.concurrent.TimeUnit;

@RestController
public class UrlShortenerController {
    private final ShortURLService shortUrlService;

    private final ClickService clickService;

    private UrlChecker urlchecker;
    private final APIAccess api_acces;

    public static final int THROTTLING_GET_LIMIT = 10;
    public static final int THROTTLING_POST_LIMIT = 10;

    public UrlShortenerController(ShortURLService shortUrlService, ClickService clickService, APIAccess api) {
        this.shortUrlService = shortUrlService;
        this.clickService = clickService;
        this.urlchecker = new UrlChecker(shortUrlService);
        this.api_acces = api;
    }

    private String final_url(String url) {
        Response response;
        try {
            response = Jsoup.connect(url).timeout(1000).userAgent("Mozilla").execute();
        } catch (HttpStatusException e) {
            e.printStackTrace();
            return url;
        } catch (IOException e3) {
            e3.printStackTrace();
            return url;
        }
        return response.url().toString();
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
            //System.out.println("STCODE: " + responseCode);
            ret = responseCode == HttpURLConnection.HTTP_OK;

        } catch (HttpStatusException e1){
            e1.printStackTrace();
            ret = false;
        } catch (UnknownHostException e2) {
            e2.printStackTrace();
            ret = false;
        } catch (IOException e3) {
            e3.printStackTrace();
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
                data = api_acces.extractInfoUserAgent(request);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(data != null && !data.isEmpty()){
                System.err.println(data.toString()+ " !!!!!!!!!!!!!!!!!!!!!");
                clickService.saveClickUserAgent(id, extractIP(request), data.get(0), data.get(1), data.get(2), request.getHeader("referer"));
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


    @GetMapping("/all")
    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public ModelAndView all(@RequestParam("page") Optional<Long> page,
                        @RequestParam("size") Optional<Long> size) {
        ModelAndView modelo = new ModelAndView("listClick");
        List<Click> lc = clickService.clicksReceived(page.orElse((long) 1), size.orElse((long) 5));
        modelo.addObject("clicks", lc);
        Long count = clickService.count();
        modelo.addObject("pages", (int) (count / size.orElse((long) 5)));
        modelo.addObject("page", page.orElse((long) 1));
        System.out.println("Pagina actual: " + page.orElse((long) 1) + " Paginas: " + (count / size.orElse((long) 5)) );
        return modelo;
    }
    
    @RequestMapping(value = "/download-data", method = RequestMethod.GET)
    public ResponseEntity<byte[]> download_all() {
        String json = clickService.clicksReceived();
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
        if (urlValidator.isValid(url) && urlchecker.isAccesible(url)) {
            if (sponsor != null && sponsor.equals("")) sponsor = null;
            if (sponsor != null && shortUrlService.findByKey(sponsor) != null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            url = final_url(url);
            
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
