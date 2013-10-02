package com.m00ware.ftpindex;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * @author Wooden
 * 
 */
public class SearchUtil2 {

    public static int[] getLastCharJumps(byte[] toFind) {
        int[] result = new int[256];
        for (int i = 0; i < 256; i++) {
            result[i] = toFind.length;
        }
        int jump = 1;
        for (int i = toFind.length - 2; i >= 0; i--) {
            int c = toFind[i];
            if (result[c] > jump) {
                result[c] = jump;
                // System.out.println((char)c+" -> "+jump);
            }
            jump++;
        }
        return result;
    }

    public static int[] getPartialMatchJumps(byte[] toFind) {
        int len = toFind.length - 1;
        int[] result = new int[len];
        for (int i = 0; i < len; i++) {
            result[i] = len + 1;
            for (int j = 0; j < len; j++) {
                boolean match = true;
                for (int k = 0; match && k <= i && j - k >= 0; k++) {
                    if (toFind[j - k] != toFind[len - k]) {
                        match = false;
                    }
                }
                if (match && (j - i - 1 < 0 || toFind[j - i - 1] != toFind[len - i - 1])) {
                    result[i] = len - j;
                }
            }
        }
        return result;
    }

    public static boolean bytesContains(byte[] toSearch, byte[] toFind, int[] lastCharJumps, int[] partialMatchJumps) throws UnsupportedEncodingException {
        int findLen = toFind.length;
        int searchLen = toSearch.length;
        if (findLen > searchLen) {
            return false;
        }
        byte last = toFind[findLen - 1];
        int i = findLen - 1;
        scan: while (i < searchLen) {
            /*
             * for(int in = 0; in < i - findLen+1; in++)System.out.print(' ');
             * System.out.println(new String(toFind,"ISO-8859-1"));
             * System.out.println(new String(toSearch,"ISO-8859-1"));
             */
            if (toSearch[i] == last) { // last char match
                for (int j = 0; j < findLen - 1; j++) {
                    if (toFind[findLen - 2 - j] != toSearch[i - 1 - j]) {
                        int lastJump = lastCharJumps[toSearch[i - 1 - j] & 0xff] - j - 1;
                        int partialJump = partialMatchJumps[j];
                        // int skip = Math.max(lastJump, partialJump);
                        // System.out.println("Skip on partial ('"+(char)toSearch[i-1-j]+"' -> "+(lastJump+j+1)+"-"+(j+1)+" ; j="+j+" -> "+partialJump+") "+skip);
                        if (lastJump > partialJump) {
                            i += lastJump;
                        } else {
                            i += partialJump;
                        }
                        continue scan;
                    }
                }
                return true;
            } else {
                int skip = lastCharJumps[toSearch[i] & 0xff];
                // System.out.println("Skip on last "+(char)toSearch[i]+" -> "+skip);
                i += skip;
            }
        }
        return false;
    }

    public static void main0(String[] args) throws UnsupportedEncodingException {
        System.out.println(Arrays.toString(getLastCharJumps("avec le".getBytes("ISO-8859-1"))));
        System.out.println(Arrays.toString(getPartialMatchJumps("avec le".getBytes("ISO-8859-1"))));
        byte[] bytes = "avec le".getBytes("ISO-8859-1");
        System.out.println(bytesContains("Index de ftp://norace.rez-gif.supelec.fr/Videos/Series/Heroes/".getBytes("ISO-8859-1"), bytes,
                                         getLastCharJumps(bytes), getPartialMatchJumps(bytes)));
    }

    public static void main/* 1 */(String[] args) throws UnsupportedEncodingException {
        String[] strs = new String[] {
                                      "ScannerThread st = new ScannerThread(new Inet4AddressRange((Inet4Address) addr, 0xfffff800));avec le serveur",
                                      "[15:40]	tu commences � m'�nerver toi...aller encore un �pisode!!!(plus qu'une dizaine avant la lib�ration lol)avec le serveur",
                                      "Index de ftp://norace.rez-gif.supelec.fr/Videos/Series/Heroes/",
                                      "Firefox ne peut �tablir de connexion avec le serveur � l'adresse localhost:8080.",
                                      "public static void main(String[] args) throws IOException, ParseException" };
        System.out.println("Java String bench");
        System.out.println("Warmup...");
        for (int i = 0; i < 100000; i++) {
            strs[i % 5].contains("avec le");
        }
        Pattern pattern = Pattern.compile("avec le");
        for (int i = 0; i < 100000; i++) {
            pattern.matcher(strs[i % 5]).find();
        }
        Charset UTF8 = Charset.forName("UTF8");
        byte[] searchBytes = "avec le".getBytes(UTF8);
        byte[][] strsBytes = new byte[strs.length][];
        for (int i = 0; i < strs.length; i++) {
            strsBytes[i] = strs[i].getBytes(UTF8);
        }
        boolean[] impossible = SearchUtil.getImpossibleArray(searchBytes);
        int md2 = SearchUtil.getMD2(searchBytes);
        for (int i = 0; i < 100000; i++) {
            SearchUtil.bytesContains(searchBytes, strsBytes[i % 5], impossible, md2);
        }
        int[] last = SearchUtil2.getLastCharJumps(searchBytes);
        int[] partial = SearchUtil2.getPartialMatchJumps(searchBytes);
        for (int i = 0; i < 100000; i++) {
            SearchUtil2.bytesContains(searchBytes, strsBytes[i % 5], last, partial);
        }
        for (int i = 0; i < 100000; i++) {
            strs[i % 5].toLowerCase();
        }
        System.out.println("Benching...");
        int iter = 1000000;
        long t = System.nanoTime();
        for (int i = 0; i < iter; i++) {
            strs[i % 5].contains("avec le serveur");
        }
        t = System.nanoTime() - t;
        System.out.println("Contains : " + iter + " iterations took " + t + "ns : " + t / iter + "ns per iteration");
        t = System.nanoTime();
        for (int i = 0; i < iter; i++) {
            strs[i % 5].toLowerCase();
        }
        t = System.nanoTime() - t;
        System.out.println("ToLower : " + iter + " iterations took " + t + "ns : " + t / iter + "ns per iteration");
        t = System.nanoTime();
        for (int i = 0; i < iter; i++) {
            pattern.matcher(strs[i % 5]).find();
        }
        t = System.nanoTime() - t;
        System.out.println("Matcher.find : " + iter + " iterations took " + t + "ns : " + t / iter + "ns per iteration");
        t = System.nanoTime();
        for (int i = 0; i < iter; i++) {
            SearchUtil.bytesContains(searchBytes, strsBytes[i % 5], impossible, md2);
        }
        t = System.nanoTime() - t;
        System.out.println("SearchUtil.bytesContains : " + iter + " iterations took " + t + "ns : " + t / iter + "ns per iteration");
        t = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            SearchUtil2.bytesContains(searchBytes, strsBytes[i % 5], last, partial);
        }
        t = System.nanoTime() - t;
        System.out.println("SearchUtil2.bytesContains : " + iter + " iterations took " + t + "ns : " + t / iter + "ns per iteration");
    }

}
