package WebCrawlerApp;
import java.io.*;
import java.net.*;
import com.beust.jcommander.*;
import Utilities.*;

import java.util.*;

/**
 * Main web-crawler that crawls the links on web pages based on
 * a score metric, determined by several factors, including the
 * words in the query
 */
public class WebCrawler {

    private PriorityQueue<URLScore> urlsToBeTraversed;
    private Set<String> seenUrls;

    @Parameter(names = "-u", description = "URL to start the crawl")
    private String startingURLStr;
    @Parameter(names = "-q", description = "Query")
    private String query;
    @Parameter(names = "-docs", description = "Directory to save downloaded pages")
    private String dirSavePages;
    @Parameter(names = "-m", description = "Max num of pages to download")
    private int maxNumPgs = 50;
    @Parameter(names = "-t", description = "Trace")
    private boolean debug = false;

    public static final String DISALLOW = "Disallow:";

    private FileWriter fileWriter;
    private PrintWriter pWriter;

    public WebCrawler() {
        urlsToBeTraversed = new PriorityQueue<URLScore>(10, new URLScoreComparator());
        seenUrls = new HashSet<String>();
    }

    private void initialize() {
        URL startUrl;
        try {
            startUrl = new URL(startingURLStr);
        } catch (MalformedURLException me) {
            System.out.println("Invalid starting URL: " + startingURLStr);
            return;
        }

        //add to URLQueue
        URLScore startURLScore = new URLScore(startUrl);
        urlsToBeTraversed.add(startURLScore);
        if (debug) {
            System.out.println("Starting search at: " + startURLScore.getURLAsString());
            System.out.println("Max number of pages set to: " + maxNumPgs);
        }

        /*
        Behind a firewall; set your proxy and port here
        */
        Properties props= new Properties(System.getProperties());
        props.put("http.proxySet", "true");
        props.put("http.proxyHost", "webcache-cup");
        props.put("http.proxyPort", "8080");

        Properties newprops = new Properties(props);
        System.setProperties(newprops);
    }

    private void crawl() {

        int numPagesProcessed = 0;
        while ((!urlsToBeTraversed.isEmpty()) && (numPagesProcessed < maxNumPgs)) {
            URLScore topScoredPage = urlsToBeTraversed.poll();

            if (debug) {
                System.out.println("Downloading: "
                    + topScoredPage.getURLAsString()
                    + ". Score = " + topScoredPage.getScore());
            }

            if (!robotSafe(topScoredPage.getURL())) {
                if (debug) {
                    System.out.println("robots.txt disallows crawling page: "
                        + topScoredPage.getURLAsString());
                }
                continue;
            }

            //Ok to crawl, issue request for page
            String pageContents =
                    downloadPageToDirectory(topScoredPage.getURL());

            seenUrls.add(topScoredPage.getURLAsString());
            numPagesProcessed ++;

            if (numPagesProcessed >= maxNumPgs) {
                if (debug) {
                    System.out.println("Max limit on number of pages reached, exiting");
                }
                return;
            }

            if (debug) {
                System.out.println("Received page: " + topScoredPage.getURLAsString());
            }

            List<String> hrefOutlinksOnCurrentPage = findHrefOutlinks(pageContents);

            processAndAddURLsOnCurrentPageToQueue(
                hrefOutlinksOnCurrentPage, topScoredPage, pageContents);
        }
    }

    private void processAndAddURLsOnCurrentPageToQueue (
            List<String> hrefOutlinksOnCurrentPage,
            URLScore topScoredPage, String pageContents) {

        for (String hrefLink : hrefOutlinksOnCurrentPage) {

            ReturnValue retval = processHrefLink(hrefLink,
                    topScoredPage.getURL(), pageContents);

            if (retval == null) {
                continue;
            }
            String link = retval.getUrl();
            String anchor = retval.getAnchor();
            List<String> prvFiveWords = retval.getPrvFiveWords();
            List<String> nextFiveWords = retval.getNextFiveWords();

            URL linkAsUrl;
            try {
                linkAsUrl = new URL(link);
            } catch (MalformedURLException me) {
                //invalid URL - skip and continue with next
                if (debug) {
                    System.out.println("Invalid URL: " + link);
                }
                continue;
            }

            String urlLink = link;
            int scoreOfLink = score(query, urlLink, anchor, prvFiveWords,
                    nextFiveWords, pageContents);

            boolean linkAlreadyVisited = seenUrls.contains(link);
            if (linkAlreadyVisited) {
                continue;
            }

            //check if priority queue currently has this outlink
            URLScore matchingUrlInQueue = null;
            for (URLScore pendingUrls : urlsToBeTraversed) {
                if (pendingUrls.getURLAsString().equals(link)) {
                    matchingUrlInQueue = pendingUrls;
                    break;
                }
            }
            //outlink already exists in queue to be processed; so just update score
            if (matchingUrlInQueue != null) {
                int origScore = matchingUrlInQueue.getScore();
                int newScore = origScore + scoreOfLink;
                URLScore newCopyOfExistingUrl = new URLScore(matchingUrlInQueue.getURL());
                newCopyOfExistingUrl.updateScore(newScore);
                urlsToBeTraversed.add(newCopyOfExistingUrl);
                if (debug) {
                    System.out.print("Adding " + scoreOfLink + " to score of: " + link + ". ");
                    System.out.println("Total Score = " + newCopyOfExistingUrl.getScore());
                }
            } else {
                URLScore newUrlScore = new URLScore(linkAsUrl);
                newUrlScore.updateScore(scoreOfLink);
                urlsToBeTraversed.add(newUrlScore);
                if (debug) {
                    System.out.println("Adding: " + link +
                        " with score = " + scoreOfLink + " to queue.");
                }
            }
        }
        System.out.println();
    }

    private int score(String queryMixedCase, String urlOfOutlink, String anchor,
                      List<String> prvFiveWords, List<String> nextFiveWords,
                      String pageContents) {
        if (queryMixedCase == null) {
            return 0;
        }
        String query = queryMixedCase.toLowerCase();
        boolean wordInQuerySubstringOfUrl = false;
        String[] queryWords = query.split("\\s+");
        Set<String> queryWordsSet = new HashSet<String>();
        String lcPageContents = pageContents.toLowerCase();
        //letters only means no punctuation.

        String urlsRemoved = blurUrls(lcPageContents);
        String replaceNewLinesWithSpace = urlsRemoved.replaceAll("\\n", " ");
        String lettersOnlyPageContents = replaceNewLinesWithSpace.replaceAll("[^a-zA-Z ]", " ");
        String[] lettersOnlyWords = lettersOnlyPageContents.split("\\s+");
        //unique words in pagecontent
        Set<String> lettersOnlyWordsSet = new HashSet<String>(Arrays.asList(lettersOnlyWords));

        for (String word: queryWords) {
            queryWordsSet.add(word);
        }
        int commonWordsBtwnQueryAndAnchor = 0;
        String urlOfOutlinkLowerCase = urlOfOutlink.toLowerCase();
        for (String word : queryWordsSet) {
            if (anchor.toLowerCase().contains(word)) {
                commonWordsBtwnQueryAndAnchor ++;
            }

            if (urlOfOutlinkLowerCase.contains(word)) {
                wordInQuerySubstringOfUrl = true;
            }
        }
        if (commonWordsBtwnQueryAndAnchor != 0) {
            return commonWordsBtwnQueryAndAnchor * 50;
        }
        if (wordInQuerySubstringOfUrl) {
            return 40;
        }
        int u = 0;
        int v = 0;
        Set<String> seenWords = new HashSet<String>();

        for (String queryWord : queryWordsSet) {
            for (String prvWord : prvFiveWords) {
                String lcPrvWord = prvWord.toLowerCase();
                String lettersOnlyPrvWord = lcPrvWord.replaceAll("[\\W]", "");
                if (seenWords.contains(lettersOnlyPrvWord)) {
                    continue;
                }
                if (lcPrvWord.contains(queryWord)) {
                    u ++;
                    seenWords.add(lettersOnlyPrvWord);
                }
            }
            for (String nextWord: nextFiveWords) {
                String lcNxtWord = nextWord.toLowerCase();
                //ignore punctuation in word
                String lettersOnlyNextWord = nextWord.replaceAll("[\\W]", "");
                if (seenWords.contains(lettersOnlyNextWord)) {
                    continue;
                }
                if (lcNxtWord.contains(queryWord)) {
                    u ++;
                    seenWords.add(lettersOnlyNextWord);
                }
            }

            if (seenWords.contains(queryWord)) {
                continue;
            }
            if (lettersOnlyWordsSet.contains(queryWord)) {
                v ++;
            }
        }
        return ((4 * u) + Math.abs(v - u));
    }

    private String blurUrls(String lcPageContents) {
        int idxUrlBegin = lcPageContents.indexOf("<a");
        int start = 0;
        while (idxUrlBegin != -1) {
            int idxUrlEnd = lcPageContents.indexOf(">", idxUrlBegin);
            lcPageContents = (lcPageContents.substring(start, idxUrlBegin - 1) +
                lcPageContents.substring(idxUrlEnd + 1));
            idxUrlBegin = lcPageContents.indexOf("<a");
        }
        return lcPageContents;
    }

    /**
     * Extract the URL, anchor text,
     * previous 5 words before <a..
     * and next five words after ../a>
     * for a given href string, ie.:
     * <a href.../a>
     *
     * if url did not end in htm or html, return null
     */
    ReturnValue processHrefLink(String hrefLink, URL oldUrl, String pageContent) {
        URL newUrl = extractUrl(hrefLink, oldUrl);
        if (newUrl == null) {
            return null;
        }
        String anchorText = extractAnchor(hrefLink);

        List<String> prevFiveWords = getPrevFiveWords(hrefLink, pageContent);
        List<String> nextFiveWords = getNextFiveWords(hrefLink, pageContent);

        return new ReturnValue(prevFiveWords, nextFiveWords, anchorText, newUrl.toString());
    }

    private List<String> getNextFiveWords(String hrefLink, String pageContent) {
        int startSearchIdx = pageContent.indexOf(hrefLink) + hrefLink.length() + 1;
        if (startSearchIdx == pageContent.length()) {
            return new ArrayList<String>();
        }
        startSearchIdx = swallowWhiteSpacesAndNewLines(startSearchIdx, pageContent);
        String contentAfterHrefLinkEnd = pageContent.substring(startSearchIdx);

        String[] words = contentAfterHrefLinkEnd.split("\\s+");
        if (words.length == 0) {
            return new ArrayList<String>();
        }
        List<String> wordsList;
        if (words.length <= 5) {
            wordsList = new ArrayList<String>(Arrays.asList(words));
        } else {
            wordsList = new ArrayList<String>();
            int count = 5;
            for (int i = 0; i < count; i ++) {
                wordsList.add(words[i]);
            }
        }
        return wordsList;
    }

    private int swallowWhiteSpacesAndNewLines(int startSearchIdx, String pageContent) {
        while ((pageContent.charAt(startSearchIdx) == ' ') ||
                (pageContent.charAt(startSearchIdx) == '\t') ||
                (pageContent.charAt(startSearchIdx) == '\n')) {
            startSearchIdx ++;
        }
        return startSearchIdx;
    }

    private List<String> getPrevFiveWords(String hrefLink, String pageContent) {
        int matchIdx = pageContent.indexOf(hrefLink);
        if (matchIdx == -1) {
            return new ArrayList<String>();
        }
        String contentBeforeMatchIdx = pageContent.substring(0, matchIdx);
        String[] words = contentBeforeMatchIdx.split("\\s+");
        if (words.length == 0) {
            return new ArrayList<String>();
        }
        List<String> wordsList;
        if (words.length <= 5) {
            wordsList = new ArrayList<String>(Arrays.asList(words));
        } else {
            wordsList = new ArrayList<String>();
            int count = 5;
            int i = words.length - 1;
            while (count > 0) {
                wordsList.add(words[i]);
                i --;
                count --;
            }
        }
        return wordsList;
    }

    private String extractAnchor(String hrefLink) {
        int idx = 0;
        int angleBracketEnd = hrefLink.indexOf(">", idx);
        int angleBracketOpen = hrefLink.indexOf("<", angleBracketEnd);
        String anchor = hrefLink.substring(angleBracketEnd + 1, angleBracketOpen);
        return anchor;
    }

    private URL extractUrl(String hrefLink, URL oldUrl) {
        //eg: <A href="MarineMammal.html">marine mammals.</A> in lower-case
        int index = 0;
        //extract URL:
        int urlStartIdx = hrefLink.indexOf("\"", index) + 1;
        int urlEndIdx = hrefLink.indexOf("\"", urlStartIdx + 1);
        String urlStr = hrefLink.substring(urlStartIdx, urlEndIdx);
        if (!urlStr.endsWith("html") && !urlStr.endsWith("htm")) {
            return null;
        }
        URL newUrl;
        try {
            newUrl = new URL(oldUrl, urlStr);
        } catch (MalformedURLException e) {
            return null;
        }
        return newUrl;
    }

    List<String> findHrefOutlinks(String pageContents) {
        List<String> outHreflinkUrls = new ArrayList<String>();
        String lcpage = pageContents;
        int index = 0, iEndAngle = 0, ihref = 0;
        while ((index = lcpage.indexOf("<A", index)) != -1) {
            //index of '>' in '.../A>'
            iEndAngle = lcpage.indexOf("/A>",index) + 2;

            String hrefStr = lcpage.substring(index, iEndAngle + 1);
            ihref = lcpage.toLowerCase().indexOf("href",index);
            index = iEndAngle;

            //filtering out unwanted html elements: not a href tag, so skip over this
            if (ihref == -1) {
                continue;
            }

            outHreflinkUrls.add(hrefStr);
        }
        return outHreflinkUrls;
    }

    private boolean robotSafe(URL url) {
        String strHost = url.getHost();
        String strRobot = "http://" + strHost + "/robots.txt";
        URL urlRobot;
        //try to create robots.txt URL
        try {
            urlRobot = new URL(strRobot);
        } catch (MalformedURLException e) {
            //something weird is happening; don't trust it
            return false;
        }
        String strCommands;
        try {
            InputStream urlRobotStream = urlRobot.openStream();
            //read in the entire page
            byte[] b = new byte[1000];
            int numBytesRead = urlRobotStream.read(b);
            strCommands = new String(b, 0, numBytesRead);
            while (numBytesRead != -1) {
                numBytesRead = urlRobotStream.read(b);
                if (numBytesRead != -1) {
                    String newCommands = new String(b, 0, numBytesRead);
                    strCommands += newCommands;
                }
            }
            urlRobotStream.close();
        } catch (IOException e) {
            //no robots.txt file; OK to search
            return true;
        }

        String strFileUrl = url.getFile();
        int index = 0;
        //check each disallow statement to see if it's for this page (using getFile())
        while ((index = strCommands.indexOf(DISALLOW, index)) != -1) {
            index += DISALLOW.length();
            String strPath = strCommands.substring(index);
            StringTokenizer st = new StringTokenizer(strPath);

            if (!st.hasMoreTokens()) {
                break;
            }
            //get first token
            String strDisallowed = st.nextToken();
            if (strFileUrl.indexOf(strDisallowed) == 0) {
                return false;
            }
        }
        return true;
    }

    /**Download the contents of the URL*/
    String downloadPageToDirectory(URL url) {
        try {
            URLConnection urlConnection = url.openConnection();
            urlConnection.setAllowUserInteraction(false);
            InputStream urlStream = urlConnection.getInputStream();
            byte[] b = new byte[1000];
            int numBytesRead = urlStream.read(b);
            String pageContent = new String(b, 0, numBytesRead);
            while (numBytesRead != -1) {
                numBytesRead = urlStream.read(b);
                if (numBytesRead != -1) {
                    String newContent = new String(b, 0, numBytesRead);
                    pageContent = pageContent + newContent;
                }
            }
            writePageContentToDisk(pageContent, url.getFile());
            return pageContent;
        } catch (IOException e) {
            System.out.println("Could not open URL " + url.toString());
            return "";
        }
    }

    //assumes unix style directory structure
    void writePageContentToDisk(String pageContent, String urlFile) throws IOException {
        String rawFileName = urlFile;
        //get the file name before the .html: eg: mammals.html in "../xyz/mammals.html"
        int lastIndexofSlash = rawFileName.lastIndexOf('/');
        String fileName = rawFileName.substring(lastIndexofSlash + 1);
        String fileSavePages = dirSavePages + "/" + fileName;
        File outFile = new File(fileSavePages);
        fileWriter = new FileWriter(outFile);
        pWriter = new PrintWriter(fileWriter);
        pWriter.println(pageContent);
        pWriter.close();
    }

    public static void main(String[] args) throws IOException {
        WebCrawler webCrawler = new WebCrawler();
        new JCommander(webCrawler, args);
        webCrawler.initialize();
        webCrawler.crawl();
    }
}
