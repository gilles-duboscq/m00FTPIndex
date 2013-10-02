package com.m00ware.ftpindex.raw;

/**
 * @author Wooden
 * 
 */
public class FixupListing extends Fixup {
    private RawDirectory listing;

    public FixupListing(int position, RawDirectory listing) {
        super(position);
        this.listing = listing;
    }

    public RawDirectory getListing() {
        return listing;
    }

    @Override
    public String toString() {
        return "FixupListing for " + listing + super.toString();
    }
}