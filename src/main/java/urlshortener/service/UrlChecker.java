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
            Response response = Jsoup.connect(url_s).timeout(1000)
                                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:71.0) Gecko/20100101 Firefox/71.0")
                                                    .execute();
            responseCode = response.statusCode();
            //System.out.println("STCODE: " + responseCode);
            ret = responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e1){
            e1.printStackTrace();
            ret = false;
        } 
        return ret;
    }

    @Scheduled(fixedDelay = 1000)
    private void periodicCheck() {
        System.out.println("HOLAAAAAAAAAAAAA");
        List<String> to_delete = new LinkedList<String>();
        for (ShortURL url : shortUrlService.all()) {
            if (!isAccesible(url.getTarget())) {
                to_delete.add(url.getHash());
            }
        }
        for (String id : to_delete) {
            shortUrlService.delete(id);
        }
    }
        

};