package org.shareables.org.shareables.utils;

import java.util.regex.Pattern;

/**
 *@author Niklas Schnelle
 */
public class ShareableUrlMatcher {
    private static final Pattern shrblUrlPattern = Pattern.compile("shrbl://([0-9a-f]+-)*[0-9a-f]+");
    private static final Pattern shrblKeyPattern = Pattern.compile("([0-9a-f]+-)*[0-9a-f]+");

    public static final boolean isShareableUrl(CharSequence input){
        return shrblUrlPattern.matcher(input).matches();
    }

    public static final boolean isShareableKey(CharSequence input){
        return shrblKeyPattern.matcher(input).matches();
    }
}
