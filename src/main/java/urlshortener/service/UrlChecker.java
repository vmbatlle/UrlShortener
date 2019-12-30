package urlshortener.service;

import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import org.jsoup.Connection.Response;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;

import com.weddini.throttling.Throttling;
import com.weddini.throttling.ThrottlingType;
import java.util.concurrent.TimeUnit;

public class UrlChecker {

    private List<ShortURL> list;

    private ShortURLService shortUrlService;

    public UrlChecker(ShortURLService shortUrlService) {
        this.list = shortUrlService.all();
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

    @Scheduled(fixedDelay = 10000)
    private void periodicCheck() {
        List<String> to_delete = new LinkedList<String>();
        for (ShortURL url : list) {
            if (!isAccesible(url.getUri().toString())) {
                to_delete.add(url.getHash());
            }
        }
        for (String id : to_delete) {
            shortUrlService.delete(id);
        }
    }
        

};