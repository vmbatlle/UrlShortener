package urlshortener.web;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import urlshortener.domain.Click;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.HandlerMapping;

import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;
import urlshortener.service.UrlChecker;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import java.util.Optional;
import java.util.Map;
import java.util.Set;

import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.weddini.throttling.Throttling;
import com.weddini.throttling.ThrottlingException;
import com.weddini.throttling.ThrottlingType;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
public class UrlShortenerController {

    private static int defaultPageSize = 100;

    private final ShortURLService shortUrlService;

    private final ClickService clickService;

    private UrlChecker urlchecker;
    private final APIAccess api_acces;

    private final CacheLoader<Long, Click> loader;
    private final LoadingCache<Long, Click> cache;

    /** All throttling units are requests per minute */
    public static final int THROTTLING_GET_LIMIT_PER_ADDR = 10;
    public static final int THROTTLING_POST_LIMIT_PER_ADDR = 10;
    public GlobalThrottling globalThrottling;
    public URIThrotlling uriThrottling;

    private static boolean firstTime = true;

    public UrlShortenerController(ShortURLService shortUrlService, 
            ClickService clickService, APIAccess api, GlobalThrottling gt,
            URIThrotlling ut) {
        this.shortUrlService = shortUrlService;
        this.clickService = clickService;
        this.urlchecker = new UrlChecker(shortUrlService);
        this.api_acces = api;
        this.globalThrottling = gt;
        this.uriThrottling = ut;

        loader = new CacheLoader<Long, Click>() {
            @Override
            public Click load(Long key) {
                return new Click(null, null, null, null, null, null, null, null);
            }
        };

        cache = CacheBuilder.newBuilder().maximumSize(defaultPageSize).build(loader);

        List<Click> lc = clickService.clicksReceived(1, defaultPageSize);

        for(Click c : lc){
            cache.put(c.getId(), c);
        }
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

    @RequestMapping(value = {"/{id:(?!link|index).*}","/{id:(?!link|index|webjars|js|bootstrap|css|img)[a-z0-9]*}/**"}, method = RequestMethod.GET)
    @Throttling(type = ThrottlingType.RemoteAddr, limit = THROTTLING_GET_LIMIT_PER_ADDR, timeUnit = TimeUnit.MINUTES)
    public ResponseEntity<?> redirectTo(@PathVariable String id, HttpServletRequest request) {
        if (!globalThrottling.acquireGet()) throw new ThrottlingException();
        if (!uriThrottling.acquire(id)) throw new ThrottlingException();
        ShortURL l = shortUrlService.findByKey(id);
        if (l == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else if (!l.getSafe()) {
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        } else {
            List<String> data = null;
            try {
                data = api_acces.extractInfoUserAgent(request);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Click cl = null;
            if(data != null && !data.isEmpty()){
                cl = clickService.saveClickUserAgent(id, extractIP(request), data.get(0), data.get(1), data.get(2), request.getHeader("referer"));
            }
            else{
                cl = clickService.saveClick(id, extractIP(request));
            }

            //Inserting into cache
            cache.put(cl.getId(),cl);

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
        } 
    }


    @GetMapping("/all")
    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public ModelAndView all(@RequestParam("page") Optional<Long> page,
                        @RequestParam("size") Optional<Long> size,
                        @RequestParam("start") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") Optional<LocalDateTime> start,
                        @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") Optional<LocalDateTime> end) {

        ModelAndView modelo = new ModelAndView("listClick");
        if(!start.isPresent()){
            if(page.orElse((long) 1) != 1){
                List<Click> lc = clickService.clicksReceived(page.orElse((long) 1), size.orElse((long) 5));
                modelo.addObject("clicks", lc);
            } else {
                System.out.println("Cogiendo valores de cache.....");
                List<Click> lc = cache.asMap().values().stream().collect(Collectors.toList());
                modelo.addObject("clicks", lc);
            }
            Long count = clickService.count();
            modelo.addObject("pages", (int) (count / size.orElse((long) 5)));
            modelo.addObject("page", page.orElse((long) 1));
            modelo.addObject("start", start.orElse(LocalDateTime.parse("2019-12-30T08:30")));
        }else{
            List<Click> lc = clickService.clicksReceivedDated(start.orElse(LocalDateTime.now()), page.orElse((long) 1), size.orElse((long) 5));
            modelo.addObject("clicks", lc);
            Long count = clickService.countByDate(start.orElse(LocalDateTime.now()));
            modelo.addObject("pages", (int) (count / size.orElse((long) 5)));
            modelo.addObject("page", page.orElse((long) 1));
            modelo.addObject("start", start.orElse(LocalDateTime.parse("2019-12-30T08:30")));
        }
        System.out.println("ventana actual: " + start.orElse(null) + ", " + end.orElse(null));
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
    @Throttling(type = ThrottlingType.RemoteAddr, limit = THROTTLING_POST_LIMIT_PER_ADDR, timeUnit = TimeUnit.MINUTES)
    public ResponseEntity<ShortURL> shortener(@RequestParam("url") String url,
                                              @RequestParam(value = "sponsor", required = false) String sponsor,
                                              HttpServletRequest request) {
        if (!globalThrottling.acquirePost()) throw new ThrottlingException();
        UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
        boolean accesible = urlchecker.isAccesible(url);
        if ((urlValidator.isValid(url) || url.contains("://localhost:")) && accesible ) {
        //if (accesible) {
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
            System.out.println("Valid = " + urlValidator.isValid(url) + " Accesible = " + accesible);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/test_scheduler", method = RequestMethod.GET)
    public ResponseEntity<ShortURL> test_scheduler() {
        if (firstTime) {
            firstTime = false;
            return new ResponseEntity<>(HttpStatus.OK);
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
