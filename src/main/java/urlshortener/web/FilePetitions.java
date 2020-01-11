package urlshortener.web;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;

import urlshortener.domain.Download;


@Component
public class FilePetitions {

    private static Map<Long, Download> downloadPetitions;

    public FilePetitions() {
        downloadPetitions = new HashMap<Long, Download>();
    }

    public void addPetition(Long id) {
        String url = "all_data_" + id;
        Download aux = downloadPetitions.get(id);
        if (aux == null) {
            aux = new Download(url, false, 1);
        } else {
            aux.setCount(aux.getCount() + 1);
        }
        downloadPetitions.put(id, aux);
    }

    public boolean existsPetition(Long id) {
        return downloadPetitions.containsKey(id);
    }

    public boolean isReady(Long id) {
        return downloadPetitions.containsKey(id) && downloadPetitions.get(id).getReady();
    }

    public String getFile(Long id) {
        if (downloadPetitions.containsKey(id)) {
            return downloadPetitions.get(id).getId();
        } else {
            return "";
        }
    }

    public Set<Long> getKeys(){
        return downloadPetitions.keySet();
    }

    public Download getDownload(Long id){
        if (downloadPetitions.containsKey(id)) {
            return downloadPetitions.get(id);
        } else {
            return null;
        }
    }

    /*@Async
    public void createFile(Long id){
        if(downloadPetitions.containsKey(id) && !downloadPetitions.get(id).getReady()){
            System.out.println("Esta la petiocion con id: " + id);
            Gson gson = new Gson();
            List<Click> clicks = clickService.allClicks();
            String filePath = "files/" + downloadPetitions.get(id).getId();
            File json = new File(filePath);
            try {
                System.out.println(clicks);
                json.createNewFile();
                Writer wr =  new FileWriter(filePath);
                gson.toJson(clicks, wr);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Puesto a ready el fichero: " + filePath);
            downloadPetitions.get(id).setReady(true);
        }
    }*/

};