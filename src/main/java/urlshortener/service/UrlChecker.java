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

@Component
public class UrlChecker {

    private ShortURLService shortUrlService;

    public UrlChecker(ShortURLService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

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
                return true;
            } else if (responseCode == 307) {
                String sNewUrl = response.header("Location");
                if (sNewUrl != null && sNewUrl.length() > 7)
                    return isAccesible(sNewUrl);
            }
        } catch (IOException e1){
            e1.printStackTrace();
            ret = false;
        }
        
        return ret;
    }

    @Scheduled(fixedDelayString = "${uri.checker.period}")
    private void periodicCheck() {
        //List<String> to_notsafe = new LinkedList<String>();
        for (ShortURL url : shortUrlService.all()) {
            if (!isAccesible(url.getTarget())) {
                url.setSafe(false);
                shortUrlService.update(url);
            }
            else if (!url.getSafe() && isAccesible(url.getTarget())) {
                url.setSafe(true);
                shortUrlService.update(url);
            }
        }
        // for (String id : to_notsafe) {
        //         url.setSafe(true);
        //         shortUrlService.update(url);
        // }
    }
        

};