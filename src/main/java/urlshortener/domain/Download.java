package urlshortener.domain;

public class Download {
    public String id;
    public boolean ready;
    public int count;

    public Download(String id, boolean ready, int count) {
        this.id = id;
        this.ready = ready;
        this.count = count;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
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
