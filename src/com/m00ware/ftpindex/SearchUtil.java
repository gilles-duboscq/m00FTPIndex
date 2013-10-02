package com.m00ware.ftpindex;

public class SearchUtil {
    public static boolean[] getImpossibleArray(byte[] toFind) {
        boolean[] impossible = new boolean[256];
        for (int i = 0; i < 256; i++) {
            impossible[i] = true;
        }
        for (int i = 0; i < toFind.length; i++) {
            impossible[toFind[i] & 0xff] = false;
        }
        return impossible;
    }

    public static int getMD2(byte[] toFind) {
        int findLen = toFind.length;
        int md2 = findLen;
        byte last = toFind[md2 - 1];
        for (int i = 0; i < findLen; i++) {
            if (last == toFind[i]) {
                md2 = findLen - i;
            }
        }
        return md2;
    }

    /**
     * this method assumes the bytes represent a UTF-8 encoded string
     * 
     * @param mixed
     * @return
     */
    public static byte[] fuzzyUTF8StrToLower(byte[] mixed) {
        byte[] lower = new byte[mixed.length];
        for (int i = 0; i < mixed.length; i++) {
            if (mixed[i] < 0x5b && mixed[i] > 0x40) {
                lower[i] = (byte) (mixed[i] + 0x20);
            } else {
                lower[i] = mixed[i];
            }
        }
        return lower;
    }

    public static boolean bytesContains(byte[] toSearch, byte[] toFind, boolean[] impossible, int md2) {
        int findLen = toFind.length;
        int searchLen = toSearch.length;
        if (findLen > searchLen) {
            return false;
        }
        byte last = toFind[findLen - 1];
        int skip;
        scan: for (int i = findLen - 1; i < searchLen;) {
            if (toSearch[i] == last) { // last char match
                for (int j = 0; j < findLen - 1; j++) {
                    if (toFind[j] != toSearch[i - findLen + 1 + j]) {
                        if (impossible[toSearch[i] & 0xff]) {
                            skip = j + 1;
                        } else {
                            skip = 1;
                        }
                        i += Math.max(skip, md2);
                        continue scan;
                    }
                }
                return true;
            }
            if (impossible[toSearch[i] & 0xff]) {
                i += findLen;
            } else {
                i++;
            }
        }
        return false;
    }
}
