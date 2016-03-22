package Utilities;
import java.util.List;

/**
 * Created by purnima on 3/16/16.
 */
public class ReturnValue {
    private List<String> prvFiveWords;
    private List<String> nextFiveWords;
    private String anchor;
    private String url;

    public ReturnValue(List<String> prvFiveWords, List<String> nextFiveWords, String anch, String url) {
        this.prvFiveWords = prvFiveWords;
        this.nextFiveWords = nextFiveWords;
        this.anchor = anch;
        this.url = url;
    }

    public List<String> getPrvFiveWords() {
        return prvFiveWords;
    }
    public List<String> getNextFiveWords() {
        return nextFiveWords;
    }
    public String getAnchor() {
        return anchor;
    }
    public String getUrl() {
        return url;
    }
}
