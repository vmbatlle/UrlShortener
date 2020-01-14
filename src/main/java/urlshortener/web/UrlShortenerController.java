package urlshortener.web;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.core.io.Resource;

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
import urlshortener.service.FileCreator;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;

import java.lang.SuppressWarnings;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.LocalDateTime;

import java.time.temporal.ChronoUnit;

import java.util.Collections;
import java.util.List;

import java.util.Optional;

import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonIOException;
import com.weddini.throttling.Throttling;
import com.weddini.throttling.ThrottlingException;
import com.weddini.throttling.ThrottlingType;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.core.io.UrlResource;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class UrlShortenerController {

    /** Default size of a page of clicks */
    private static long defaultPageSize = 5;

    /** Service storing the created short URLs */
    private final ShortURLService shortUrlService;

    /** Service storing the clicks made on short URLs */
    private final ClickService clickService;

    private UrlChecker urlchecker; /**< Check whether an URL is reachable **/
    private final APIAccess api_acces; /**< API for User-Agent info extraction **/

    private final FilePetitions file_petitions; /**< Manager for bulk downloads **/
    @SuppressWarnings("unused")
    private final FileCreator file_creator; /**< Periodic file creation **/

    private final CacheLoader<Long, Click> loader; /**< Loader for cached clicks **/
    private final LoadingCache<Long, Click> cache; /**< Cached clicks **/
    

    /** All throttling units are requests per minute */
    /** Max. number of GET request per minute and IP address */
    public static final int THROTTLING_GET_LIMIT_PER_ADDR = 100;
    /** Max. number of POST request per minute and IP address */
    public static final int THROTTLING_POST_LIMIT_PER_ADDR = 100;
    public GlobalThrottling globalThrottling; /**< Limitter of global requests **/
    public URIThrotlling uriThrottling; /**< Limitter of request per URI **/

    /**
     * (constructor)
     * @param shortUrlService  the storage for short URLs
     * @param clickService  the storage for clicks on short URLs
     * @param api  the parser for User-Agent information
     * @param gt  the limitter for global requests
     * @param ut  the limitter for requests per URI
     */
    public UrlShortenerController(ShortURLService shortUrlService, 
            ClickService clickService, APIAccess api, GlobalThrottling gt,
            URIThrotlling ut) {
        this.shortUrlService = shortUrlService;
        this.clickService = clickService;
        this.urlchecker = new UrlChecker(shortUrlService);
        this.api_acces = api;
        this.file_petitions = new FilePetitions();
        this.file_creator = new FileCreator(file_petitions,clickService);
        this.globalThrottling = gt;
        this.uriThrottling = ut;

        loader = new CacheLoader<Long, Click>() {
            @Override
            public Click load(Long key) {
                return new Click(null, null, null, null, null, null, null, null);
            }
        };

        /** Pre-fill cache of clicks with last clicks from database, if any */
        cache = CacheBuilder.newBuilder().maximumSize(defaultPageSize).build(loader);
        List<Click> lc = clickService.clicksReceived(1, defaultPageSize);
        for (Click c : lc) {
            cache.put(c.getId(), c);
        }
    }

    /**
     * Get the final URI given an URL with 30X redirections.
     * @param url  the original URL
     * @return  the final URI after following redirections.
     */
    private String final_url(String url) {
        Response response;
        try {
            /** Using Jsoup for checking if reachable and getting final URI */
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


    /**
     * Handles translation between short URLs and targer URIs.
     * @param id  the unique identifier of an short URL
     * @param request  the data of the HTTP request
     * @return  30X redirection iff short URL exists, 40X otherwise.
     */
    @RequestMapping(value = {"/{id:(?!link|index).*}","/{id:(?!link|index|webjars|js|bootstrap|css|img)[a-z0-9]*}/**"}, method = RequestMethod.GET)
    @Throttling(type = ThrottlingType.RemoteAddr, limit = THROTTLING_GET_LIMIT_PER_ADDR, timeUnit = TimeUnit.MINUTES)
    public ResponseEntity<?> redirectTo(@PathVariable String id, HttpServletRequest request) {
        /** Gloabl rate limitter */
        if (!globalThrottling.acquireGet()) throw new ThrottlingException();
        /** Local rate limitter */
        if (!uriThrottling.acquire(id)) throw new ThrottlingException();

        ShortURL l = shortUrlService.findByKey(id);
        if (l == null) {
            /** 404 - The identifier is not in the database */
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else if (!l.getSafe()) {
            /** 409 - The URI is not safe to access */
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        } else {
            List<String> data = null;
            try {
                /** Get User-Agent info from incoming request */
                data = api_acces.extractInfoUserAgent(request);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Click cl = null;
            if (data != null && !data.isEmpty()) {
                /** 
                 * If {@code data} has been extracted, 
                 * a full {@code Click} is created
                 */
                cl = clickService.saveClickUserAgent(id, extractIP(request), data.get(0), data.get(1), data.get(2),
                        request.getHeader("referer"));
            } else {
                /**
                 * Else, the new {@code Click} will contain only IP address
                 */
                cl = clickService.saveClick(id, extractIP(request));
            }
          
            /** Inserting new {@code Click} into cache */
            cache.put(cl.getId(), cl);

            /** Preapare final URI with original path and query params */
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            String restOfTheUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            if (restOfTheUrl != null && restOfTheUrl.length() > 9) {
                restOfTheUrl = restOfTheUrl.substring(restOfTheUrl.indexOf("/",2));
            } else {
                restOfTheUrl = "";
            }
            return createSuccessfulRedirectToResponse(l, restOfTheUrl, params);
        } 
    }

    /**
     * Gets the clicks stored in the database, page by page.
     * @param page  the number of page to retrieve (default = {@code defaultPageSize})
     * @param start  the minimum date of a click (default = 1970-01-01T00:00)
     * @param end  the maximum date of a click (default = {@code LocalDateTime.now()})
     * @return  the {@code page}-th page of the list of clicks made on a
     *          short URL between {@code start} and {@code end}.
     */
    @GetMapping("/all")
    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public ModelAndView all(@RequestParam("page") Optional<Long> page,
            @RequestParam("start") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") Optional<LocalDateTime> start,
            @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") Optional<LocalDateTime> end) {

        ModelAndView modelo = new ModelAndView("listClick");
        // If there is no date get all page clicks
        if (!start.isPresent()) {
            modelo.addObject("window", false);
            // If the first page is asked use the cache, better performance
            if (page.orElse((long) 1) != 1) {
                List<Click> lc = clickService.clicksReceived(page.orElse((long) 1), defaultPageSize);
                modelo.addObject("clicks", lc);
            } else {
                List<Click> lc = cache.asMap().values().stream().collect(Collectors.toList());
                Collections.sort(lc, Collections.reverseOrder());
                modelo.addObject("clicks", lc);
            }

            Long count = clickService.count();
            // Total number of pages
            modelo.addObject("pages",
                (int) Math.ceil((double)(count) / (double) defaultPageSize));
            modelo.addObject("page", page.orElse((long) 1));
            modelo.addObject("start",
                start.orElse(LocalDateTime.parse("1970-01-01T00:00")));
            modelo.addObject("end",
                end.orElse(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)));

        } else {
            // Value to use href with or without temporal window, used if "Window" = true
            modelo.addObject("window", true);

            List<Click> lc = clickService.clicksReceivedDated(
                    start.orElse(LocalDateTime.now()),
                    end.orElse(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)),
                    page.orElse((long) 1),
                    defaultPageSize);
            modelo.addObject("clicks", lc);
            Long count = clickService.countByDate(
                start.orElse(LocalDateTime.now()),
                end.orElse(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)));
            modelo.addObject("pages",
                (int) Math.ceil((double)(count) / (double) defaultPageSize));
            modelo.addObject("page", page.orElse((long) 1));
            modelo.addObject("start",
                start.orElse(LocalDateTime.parse("1970-01-01T00:00")));
            modelo.addObject("end",
                end.orElse(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)));
        }
        return modelo;
    }

    /**
     * Handles a new request for downloading bulk data.
     * @return the success view if there are some clicks stored
     *         and it's possible to generate a file.
     */
    @RequestMapping(value = "/download-data", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelAndView download_data() {
        ModelAndView modelo = new ModelAndView("descarga");
        List<Click> lastClick = clickService.clicksReceived(1, 1);

        // If there aren't clicks show error message to client
        if(lastClick.size() == 0){
            modelo.addObject("pending", false);
        } else {
            //Add a new download petition from click id to the first one
            Long id = lastClick.get(0).getId();
            modelo.addObject("pending", true);
            file_petitions.addPetition(id);
            String url = "all_data_" + id;
            modelo.addObject("url", url);
        }
        return modelo;
    }

    /** Message to display when the file creation is not completed */
    private static final String DOWNLOAD_NO_CONTENT_MSG = 
        "The file is not ready yet, try again later.";

    /** 
     * Message to display when the given {@code id}
     * does not refer to valid download request
     */
    private static final String DOWNLOAD_NOT_FOUND_MSG = 
        "There is no download request with that id.";

    /**
     * Handles an old request for downloading bulk data.
     * @param id  special code to select the download file (@see download_data)
     * @return the file to be downloaded.
     * @throws JsonIOException  when cannot convert file to JSON.
     * @throws IOException  when cannot read requested file.
     */
    @RequestMapping(value = "/dwn/all_data_{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> download_all(@PathVariable Long id) throws JsonIOException, IOException {
        if(file_petitions.existsPetition(id)){// If the id is correct, check availability of the file
            if(file_petitions.isReady(id)){
                String fileName = file_petitions.getFile(id) + ".json";
                Path p = Paths.get("./files/" + fileName);
                Resource r = new UrlResource(p.toUri());
                HttpHeaders respHeaders = new HttpHeaders();
                respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
                respHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
                return new ResponseEntity<Resource>(r, respHeaders, HttpStatus.OK);
            } else {
                return new ResponseEntity<String>(DOWNLOAD_NO_CONTENT_MSG, null, HttpStatus.NO_CONTENT);
            }
        } else {
            // ModelAndView modelo = new ModelAndView("badrequest");
            return new ResponseEntity<String>(DOWNLOAD_NOT_FOUND_MSG, null, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Adds a new short URL.
     * @param url  the target URL to shorten
     * @param sponsor  the custom id to the new short URL
     * @param request  the data from the incoming HTTP request
     * @return 201 iff the new URL has been created, 400 otherwise.
     */
    @RequestMapping(value = "/link", method = RequestMethod.POST)
    @Throttling(type = ThrottlingType.RemoteAddr, limit = THROTTLING_POST_LIMIT_PER_ADDR, timeUnit = TimeUnit.MINUTES)
    public ResponseEntity<ShortURL> shortener(@RequestParam("url") String url,
                                              @RequestParam(value = "sponsor", required = false) String sponsor,
                                              HttpServletRequest request) {
        /** Global rate limitter */
        if (!globalThrottling.acquirePost()) throw new ThrottlingException();

        /** Check if the URL is valid and reachable */
        UrlValidator urlValidator = new UrlValidator(new String[]{"http", "https"});
        boolean accesible = urlchecker.isAccesible(url);
    
        if ((urlValidator.isValid(url) || url.contains("://localhost:")) && accesible ) {
            if (sponsor != null && sponsor.equals("")) sponsor = null;
            if (sponsor != null && shortUrlService.findByKey(sponsor) != null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            url = final_url(url);
            
            /** Store the final short URL data */
            ShortURL su = shortUrlService.save(url, sponsor, request.getRemoteAddr());                                               
            HttpHeaders h = new HttpHeaders();
            h.setLocation(su.getUri());
            return new ResponseEntity<>(su, h, HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /** Stores if the {@code /test_schduler} URI has not been accessed yet */
    private static boolean firstTime = true;

    /**
     * Custom URI used for testing purposes.
     * @return 200 the first time accessed. 400 the following times.
     */
    @RequestMapping(value = "/test_scheduler", method = RequestMethod.GET)
    public ResponseEntity<ShortURL> test_scheduler() {
        if (firstTime) {
            firstTime = false;
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            firstTime = true;
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
    
    /**
     * Gets the origin IP address of a given HTTP request.
     * @param request  the incoming request
     * @return the IP address of the remote host.
     */
    private String extractIP(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /**
     * Build the redirection response for the {@code redirectTo} method.
     * @param l  the short URL data
     * @param restURL  the original URL data
     * @param params  ther original URL params
     * @return the redirection to send to the client.
     */
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
