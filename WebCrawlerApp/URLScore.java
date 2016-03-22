package WebCrawlerApp;

import java.net.URL;

/**
 * Created by purnima on 3/14/16.
 */
public class URLScore {
    private URL url;
    private int score;

    /**
     * Default score, 0, for any new url-score object
     */
    public URLScore(URL url) {
        this.url = url;
        score = 0;
    }
    public URL getURL() {
        return url;
    }
    public int getScore() {
        return score;
    }

    public void updateScore(int newScore) {
        score = newScore;
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof URLScore)) {
            return false;
        }
        if (o == this) {
            return true;
        }
        URLScore otherObject = (URLScore) o;
        return (otherObject.url.toString().equals(url.toString()));
    }

    public String getURLAsString() {
        return url.toString();
    }
}
