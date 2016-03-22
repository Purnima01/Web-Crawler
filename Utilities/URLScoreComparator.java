package Utilities;
import WebCrawlerApp.*;

import java.util.Comparator;

/**
 * Compares/Sorts two URLScore objects on the basis of their score fields
 */
public class URLScoreComparator implements Comparator<URLScore> {
    public int compare(URLScore o1, URLScore o2) {
        if (o1.getScore() == o2.getScore()) {
            return -1;
        }
        if (o1.getScore() > o2.getScore()) {
            return -1;
        }
        return 1;
    }
}
