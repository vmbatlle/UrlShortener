package urlshortener.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import urlshortener.domain.ShortURL;
import urlshortener.service.ShortURLService;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;

import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

/**
 * UrlChecker
 * Class to check if the url is accesible getting 400 code
 * Scheduling event that checks the accessibility of urls on the DB 
 */
@Component
public class UrlChecker {

    private ShortURLService shortUrlService;

    /**
     * Constructor
     * @param shortUrlService
     */
    public UrlChecker(ShortURLService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }
    /**
     * Checks the accessibility of an uni
     * @param url_s
     * @return the accessibility of the url (true or false)
     */
    public boolean isAccesible(String url_s) {
        
        boolean ret = false;
        try {           
            int responseCode = 400;
            Response response = Jsoup.connect(url_s).timeout(1000)
                                                    .followRedirects(true)
                                                    .ignoreHttpErrors(true)
                                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:71.0) Gecko/20100101 Firefox/71.0")
                                                    .execute();
            responseCode = response.statusCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                ret = true;
            } else if (responseCode == 307) {
                String sNewUrl = response.header("Location");
                if (sNewUrl != null && sNewUrl.length() > 7)
                    ret = isAccesible(sNewUrl);
            }
        } catch (IOException e1){
            //e1.printStackTrace();
            ret = false;
        }
        return ret;
    }

    /**
     * Checks the uris of the db each a period
     */
    @Scheduled(fixedDelayString = "${uri.checker.period}")
    private void periodicCheck() {
        //System.out.println("HOLAAA: ");
        for (ShortURL url : shortUrlService.all()) {
            //System.out.println("cheking: " + url.getTarget());
            boolean isAccesible = isAccesible(url.getTarget());
            if (!isAccesible) {
                url.setSafe(false);
                //System.out.println("MARK NOT SAFE: " + url.getHash());
                shortUrlService.update(url);
            }
            else if (!url.getSafe() && isAccesible) {
                url.setSafe(true);
                shortUrlService.update(url);
                //System.out.println("MARK SAFE: " + url.getHash());
            }
        }
        for (ShortURL url : shortUrlService.all()) {
            if (!url.getSafe()) {
                //System.out.println("NOT SAFE: " + url.getHash());
            }
        }
    }
        

};