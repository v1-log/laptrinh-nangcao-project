package com.auction.model;

public class Art extends com.auction.model.Item {
  private String artist;

  public Art(String id, String name, String description,
             double startingPrice, String artist) {
    super(id, name, description, startingPrice);
    this.artist = artist;
  }

  public String getArtist() {
    return artist;
  }

  @Override
  public ItemType getItemType() {
    return ItemType.ART;
  }

  @Override
  public void printInfo() {
    System.out.println("Art: " + getName()
            + " | Price: " + getCurrentPrice()
            + " | Artist: " + artist);
  }
}
