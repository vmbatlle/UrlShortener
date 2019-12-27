package urlshortener.web;

import org.apache.commons.validator.routines.UrlValidator;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
//import org.springframework.web.bind.annotation.*;

import urlshortener.domain.Click;
import urlshortener.domain.ShortURL;
import urlshortener.service.ClickService;
import urlshortener.service.ShortURLService;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.HttpUrl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;


@Component
public class APIAccess {
    @Value("${userstack.key}")
    private String userStackKey;

    public List<String> extractInfoUserAgent(HttpServletRequest ua_request)
            throws MalformedURLException, IOException {
        OkHttpClient client = new OkHttpClient();

        //String param2 = "e4edfb3090e960cd96d7a9df73acc622"; // API-KEY
        HttpUrl.Builder urlBuilder = HttpUrl.parse("http://api.userstack.com/detect?").newBuilder();
        urlBuilder.addQueryParameter("access_key", userStackKey);
        System.err.println("Key guardada en properties: " + userStackKey);
        urlBuilder.addQueryParameter("ua", ua_request.getHeader("User-Agent"));
        String url = urlBuilder.build().toString();
        //System.out.println("Url generada: " + url );
        Request request = new Request.Builder()
            .url(url)
            .build();
        
        //ObjectMapper objectMapper = new ObjectMapper(); 
        ResponseBody responseBody = client.newCall(request).execute().body(); 
        //String json_response = objectMapper.readValue(responseBody.string(), String.class);
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(responseBody.string());

        List<String> data = new ArrayList<>();
        try{
            //System.out.println(responseBody.string());
            data.add(JsonPath.read(document, "$['os']['name']"));
            data.add(JsonPath.read(document, "$['device']['type']"));
            data.add(JsonPath.read(document, "$['browser']['name']"));
        }catch(Exception e){
            System.err.println("Ha fallado JsonPath............................!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            data.add("fallo");
            data.add("fallo");
            data.add("fallo");
        }

        return data;
    }
}