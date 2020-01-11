package urlshortener.domain;

/**
 * Class that simbolizes a download petition. 
 */
public class Download {
    public String id; /** url that defines the download */
    public Long lastClick; /** Last click stored when the petition was made */
    public boolean ready; /** Flag that indicates if the dwonload file is done */
    public int count; /** Number of petitions */

    public Download(String id, Long lc, boolean ready, int count) {
        this.id = id;
        this.lastClick = lc;
        this.ready = ready;
        this.count = count;
    }

    public String getId() {
        return this.id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Long getLastClick() {
        return this.lastClick;
    }

    public void setLastClick(Long lc) {
        this.lastClick = lc;
    }

    public boolean isReady() {
        return this.ready;
    }

    public boolean getReady() {
        return this.ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public int getCount() {
        return this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}
