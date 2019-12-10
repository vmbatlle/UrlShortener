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
import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;

import javax.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.json.JSONObject;

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
                data = APIAccess.extractInfoUserAgent(request);
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

    /*@RequestMapping(value = "/all", method = RequestMethod.GET)
    public List<Click> all(@RequestParam("page") Optional<Long> page,
                        @RequestParam("size") Optional<Long> size) {
        return clickService.clicksReceived(page.orElse((long) 1), size.orElse((long) 100));
    }*/

    @GetMapping("/all")
    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public ModelAndView all(/*Model model,*/ @RequestParam("page") Optional<Long> page,
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

    private ResponseEntity<?> createSuccessfulRedirectToResponse(ShortURL l) {
        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create(l.getTarget()));
        return new ResponseEntity<>(h, HttpStatus.valueOf(l.getMode()));
    }
}
