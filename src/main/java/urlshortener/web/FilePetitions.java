package urlshortener.web;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import urlshortener.domain.Download;


@Component
public class FilePetitions {

    // Store of download petitions
    private static Map<Long, Download> downloadPetitions;

    public FilePetitions() {
        downloadPetitions = new HashMap<Long, Download>();
    }

    /**
     * Add a new petition if not exists 
     * @param id Identificator of new petition
     */
    public synchronized void addPetition(Long id) {
        String url = "all_data_" + id;
        Download aux = downloadPetitions.get(id);
        if (aux == null) {// If not exists create a new petition
            aux = new Download(url, id, false, 1);
        } else {// Increase number of id petitions
            aux.setCount(aux.getCount() + 1);
        }
        downloadPetitions.put(id, aux);
    }

    /**
     * 
     * @param id petition searched
     * @return true if exixts a petition with identificator id
     */
    public synchronized boolean existsPetition(Long id) {
        return downloadPetitions.containsKey(id);
    }

    /**
     * 
     * @param id petition searched
     * @return true if the file has been generated for petition id
     */
    public synchronized boolean isReady(Long id) {
        return downloadPetitions.containsKey(id) && downloadPetitions.get(id).getReady();
    }

    /**
     * 
     * @param id petition searched
     * @return name of the petition id if exists, empty string otherwise
     */
    public synchronized String getFile(Long id) {
        if (downloadPetitions.containsKey(id)) {
            return downloadPetitions.get(id).getId();
        } else {
            return "";
        }
    }

    /**
     * 
     * @return all keys stored
     */
    public synchronized Set<Long> getKeys(){
        return downloadPetitions.keySet();
    }

    /**
     * 
     * @param id petition searched
     * @return the download related to petition id if exists, null otherwise
     */
    public synchronized Download getDownload(Long id){
        if (downloadPetitions.containsKey(id)) {
            return downloadPetitions.get(id);
        } else {
            return null;
        }
    }

};