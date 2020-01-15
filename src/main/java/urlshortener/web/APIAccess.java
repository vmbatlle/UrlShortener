package urlshortener.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.HttpUrl;

import java.io.IOException;
import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.List;


@Component
public class APIAccess {
    @Value("${userstack.key:0}")
    private String userStackKey;

    public List<String> extractInfoUserAgent(HttpServletRequest ua_request)
            throws MalformedURLException, IOException {
        OkHttpClient client = new OkHttpClient();

        //String param2 = "e4edfb3090e960cd96d7a9df73acc622"; // API-KEY
        HttpUrl.Builder urlBuilder = HttpUrl.parse("http://api.userstack.com/detect?").newBuilder();
        urlBuilder.addQueryParameter("access_key", userStackKey);
        //System.err.println("Key guardada en properties: " + userStackKey);
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
            //System.err.println("Ha fallado JsonPath............................!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            data.add("fallo");
            data.add("fallo");
            data.add("fallo");
        }

        return data;
    }
}