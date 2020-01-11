package urlshortener.web;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
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
import urlshortener.service.FileCreator;

import javax.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import java.util.Optional;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.squareup.okhttp.Headers;
import com.weddini.throttling.Throttling;
import com.weddini.throttling.ThrottlingType;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.core.io.UrlResource;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class UrlShortenerController {

    private static long defaultPageSize = 5;

    private final ShortURLService shortUrlService;

    private final ClickService clickService;

    private UrlChecker urlchecker;
    private final APIAccess api_acces;

    private final FilePetitions file_petitions;
    private final FileCreator file_creator;

    private final CacheLoader<Long, Click> loader;
    private final LoadingCache<Long, Click> cache;
    

    public static final int THROTTLING_GET_LIMIT = 10;
    public static final int THROTTLING_POST_LIMIT = 10;

    public UrlShortenerController(ShortURLService shortUrlService, ClickService clickService, APIAccess api){
        this.shortUrlService = shortUrlService;
        this.clickService = clickService;
        this.urlchecker = new UrlChecker(shortUrlService);
        this.api_acces = api;
        this.file_petitions = new FilePetitions();
        this.file_creator = new FileCreator(file_petitions,clickService);

        loader = new CacheLoader<Long, Click>() {
            @Override
            public Click load(Long key) {
                return new Click(null, null, null, null, null, null, null, null);
            }
        };

        cache = CacheBuilder.newBuilder().maximumSize(defaultPageSize).build(loader);

        List<Click> lc = clickService.clicksReceived(1, defaultPageSize);

        for (Click c : lc) {
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

    @RequestMapping(value = { "/{id:(?!link|index).*}",
            "/{id:(?!link|index|webjars|js|bootstrap)[a-z0-9]*}/**" }, method = RequestMethod.GET)
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
            Click cl = null;
            if (data != null && !data.isEmpty()) {
                cl = clickService.saveClickUserAgent(id, extractIP(request), data.get(0), data.get(1), data.get(2),
                        request.getHeader("referer"));
            } else {
                cl = clickService.saveClick(id, extractIP(request));
            }
            // Inserting into cache
            cache.put(cl.getId(), cl);

            Map<String, String[]> params_map = request.getParameterMap();
            Set<String> params_keys = params_map.keySet();

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            for (String key : params_keys) {
                System.out.println(key);
                params.addAll(key, Arrays.asList(params_map.get(key)));
            }
            String restOfTheUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            // String restOfTheUrl = (String) request.getRequestURI();

            if (restOfTheUrl != null && restOfTheUrl.length() > 9) {
                restOfTheUrl = restOfTheUrl.substring(restOfTheUrl.indexOf("/", 2));
                // System.out.println(restOfTheUrl);
            } else {
                restOfTheUrl = "";
            }
            // if (path != null) System.out.println(path);
            return createSuccessfulRedirectToResponse(l, restOfTheUrl, params);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/all")
    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public ModelAndView all(@RequestParam("page") Optional<Long> page,
            @RequestParam("start") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") Optional<LocalDateTime> start,
            @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") Optional<LocalDateTime> end) {

        ModelAndView modelo = new ModelAndView("listClick");
        if (!start.isPresent()) {
            if (page.orElse((long) 1) != 1) {
                List<Click> lc = clickService.clicksReceived(page.orElse((long) 1), defaultPageSize);
                modelo.addObject("clicks", lc);
            } else {
                List<Click> lc = cache.asMap().values().stream().collect(Collectors.toList());
                Collections.sort(lc, Collections.reverseOrder());
                modelo.addObject("clicks", lc);
            }
            Long count = clickService.count();
            modelo.addObject("pages", (int) Math.ceil((double)(count) / (double) defaultPageSize));
            modelo.addObject("page", page.orElse((long) 1));
            modelo.addObject("start", start.orElse(LocalDateTime.parse("2019-12-30T08:30")));
        } else {
            List<Click> lc = clickService.clicksReceivedDated(start.orElse(LocalDateTime.now()), page.orElse((long) 1),
                    defaultPageSize);
            modelo.addObject("clicks", lc);
            Long count = clickService.countByDate(start.orElse(LocalDateTime.now()));
            modelo.addObject("pages", (int) Math.ceil((double)(count) / (double) defaultPageSize));
            modelo.addObject("page", page.orElse((long) 1));
            modelo.addObject("start", start.orElse(LocalDateTime.parse("2019-12-30T08:30")));
        }
        return modelo;
    }

    @RequestMapping(value = "/download-data", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelAndView download_data() {
        ModelAndView modelo = new ModelAndView("descarga");
        List<Click> lastClick = clickService.clicksReceived(1, 1);
        if(lastClick.size() == 0){
            modelo.addObject("pending", false);
        } else {
            Long id = lastClick.get(0).getId();
            modelo.addObject("pending", true);
            file_petitions.addPetition(id);
            String url = "all_data_" + id;
            modelo.addObject("url", url);
        }
        return modelo;
    }

    @RequestMapping(value = "/dwn/all_data_{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> download_all(@PathVariable Long id) throws JsonIOException, IOException {
        if(file_petitions.existsPetition(id)){
            if(file_petitions.isReady(id)){
                String fileName = file_petitions.getFile(id) + ".json";
                Path p = Paths.get("./files/" + fileName);
                Resource r = new UrlResource(p.toUri());
                HttpHeaders respHeaders = new HttpHeaders();
                respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
                respHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
                return new ResponseEntity<Resource>(r, respHeaders, HttpStatus.OK);
            } else {
                return new ResponseEntity<String>("Lo sentimos pero el archivo no está listo, vuelva a intentarlo más tarde", null, HttpStatus.NO_CONTENT);
            }
        } else {
            // ModelAndView modelo = new ModelAndView("badrequest");
            return new ResponseEntity<String>("Lo sentimos pero no hay ninguna peticion de descarga con ese id", null, HttpStatus.NOT_FOUND);
        }
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
