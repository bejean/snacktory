package de.jetwick.snacktory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * @author goose | jim
 * @author karussell
 *
 * this class will be responsible for taking our top node and stripping out junk
 * we don't want and getting it ready for how we want it presented to the user
 */
public class OutputFormatter {

    public static final int MIN_PARAGRAPH_TEXT = 50;
    private static final List<String> NODES_TO_REPLACE = Arrays.asList("strong", "b", "i");
    private Pattern unlikelyPattern = Pattern.compile("display\\:none|visibility\\:hidden");
    protected final int minParagraphText;
    protected final List<String> nodesToReplace;
    protected String nodesToKeepCssSelector = "p";
    private static final int FORMAT_TEXT = 1;
    private static final int FORMAT_HTML = 2;
    public static final int FORMAT_OPTION_NO_IMG = 1;
    private int formatOption = 0;

    public OutputFormatter() {
        this(MIN_PARAGRAPH_TEXT, NODES_TO_REPLACE);
    }

    public OutputFormatter(int minParagraphText) {
        this(minParagraphText, NODES_TO_REPLACE);
    }

    public OutputFormatter(int minParagraphText, List<String> nodesToReplace) {
        this.minParagraphText = minParagraphText;
        this.nodesToReplace = nodesToReplace;
    }

    /**
     * set elements to keep in output text
     */
    public void setNodesToKeepCssSelector(String nodesToKeepCssSelector) {
        this.nodesToKeepCssSelector = nodesToKeepCssSelector;
    }

    /**
     * set elements to keep in output text
     */
    public void setFormatOption(int formatOption) {
        this.formatOption = formatOption;
    }

    /**
     * takes an element and turns the P tags into \n\n
     */
    public String getFormattedText(Element topNode) {
    	return getFormatted(topNode, FORMAT_TEXT);
    }

    /**
     * takes an element and keep basic html formating (P, SPAN, ...)
     */
    public String getFormattedHtml(Element topNode) {
    	return getFormatted(topNode, FORMAT_HTML);
    }
    
    private String getFormatted(Element topNode, int format) {
        //Element node = removeNodesWithNegativeScores(topNode, format);
    	removeNodesWithNegativeScores(topNode, format);
        StringBuilder sb = new StringBuilder();
        append(topNode, sb, nodesToKeepCssSelector, format, formatOption);
        String str = SHelper.innerTrim(sb.toString());
        if (str.length() > 100)
            return str;

        // no subelements
        if (str.isEmpty() || !topNode.text().isEmpty() && str.length() <= topNode.ownText().length())
            str = topNode.text();

        // if jsoup failed to parse the whole html now parse this smaller 
        // snippet again to avoid html tags disturbing our text:
        return Jsoup.parse(str).text();
    }

    /**
     * If there are elements inside our top node that have a negative gravity
     * score remove them
     */
    protected void removeNodesWithNegativeScores(Element topNode, int format) {
    	
    	//Element clonedTopNode = topNode.clone();
        //Elements gravityItems = clonedTopNode.select("*[gravityScore]");
    	Elements gravityItems = topNode.select("*[gravityScore]");
        for (Element item : gravityItems) {
        	//if (format == FORMAT_HTML && ("img".equals(item.tagName()) || item.select("img").size()>0)) 
        	//	continue;
            int score = Integer.parseInt(item.attr("gravityScore"));
            if (score < 0 || item.text().length() < minParagraphText) {
            	if (format == FORMAT_HTML && ("img".equals(item.tagName()) || item.select("img").size()>0)) 
            		continue;
                item.remove();
            }
        }
        //return clonedTopNode;
    }

    protected void append(Element node, StringBuilder sb, String tagName, int format, int option) {
        // is select more costly then getElementsByTag?

    	Element lastEl = null;
        MAIN:
        for (Element e : node.select(tagName)) {
            Element tmpEl = e;
            // check all elements until 'node'
            while (tmpEl != null && !tmpEl.equals(node)) {
                if (unlikely(tmpEl)) {
                	// if not wanted
                    continue MAIN;
                }
                tmpEl = tmpEl.parent();
                if (lastEl!=null && lastEl.equals(tmpEl)) {
                	// if already pushed due to a parent node
                    continue MAIN;
                }
            }

            String text = node2Text(e);
            if (format==FORMAT_TEXT || !"img".equals(e.tagName()))
	            if (text.isEmpty() || text.length() < minParagraphText || text.length() > SHelper.countLetters(text) * 2)
	                continue;

            lastEl = e;
            if (format==FORMAT_TEXT) {
                sb.append(text);
            	sb.append("\n\n");
            } else {
            	if (!"img".equals(e.tagName())) {
            		if ((option & FORMAT_OPTION_NO_IMG) == FORMAT_OPTION_NO_IMG) {
            			e.select("img").remove();
            		}
            		sb.append("<" + e.tagName() + ">" + e.html() + "</" + e.tagName() + ">\n");
            	} else {
            		if ((option & FORMAT_OPTION_NO_IMG) == 0) {
	            		// TODO : Keep only src, width and height attributes
	            		sb.append(e.outerHtml()+"\n");
            		}
            	}
            }
        }
    }

    boolean unlikely(Node e) {
        if (e.attr("class") != null && e.attr("class").toLowerCase().contains("caption"))
            return true;

        String style = e.attr("style");
        String clazz = e.attr("class");
        if (unlikelyPattern.matcher(style).find() || unlikelyPattern.matcher(clazz).find())
            return true;
        return false;
    }

    void appendTextSkipHidden(Element e, StringBuilder accum) {
        for (Node child : e.childNodes()) {
            if (unlikely(child))
                continue;
            if (child instanceof TextNode) {
                TextNode textNode = (TextNode) child;
                String txt = textNode.text();
                accum.append(txt);
            } else if (child instanceof Element) {
                Element element = (Element) child;
                if (accum.length() > 0 && element.isBlock() && !lastCharIsWhitespace(accum))
                    accum.append(" ");
                else if (element.tagName().equals("br"))
                    accum.append(" ");
                appendTextSkipHidden(element, accum);
            }
        }
    }

    boolean lastCharIsWhitespace(StringBuilder accum) {
        if (accum.length() == 0)
            return false;
        return Character.isWhitespace(accum.charAt(accum.length() - 1));
    }

    protected String node2TextOld(Element el) {
        return el.text();
    }

    protected String node2Text(Element el) {
        StringBuilder sb = new StringBuilder(200);
        appendTextSkipHidden(el, sb);
        return sb.toString();
    }

    public OutputFormatter setUnlikelyPattern(String unlikelyPattern) {
        this.unlikelyPattern = Pattern.compile(unlikelyPattern);
        return this;
    }

    public OutputFormatter appendUnlikelyPattern(String str) {
        return setUnlikelyPattern(unlikelyPattern.toString() + "|" + str);
    }
}
