package com.deakin.lostandfound;

/**
 * Plain old Java object representing one Lost or Found advert.
 * Extended in 9.1 to carry lat/lng so items can be pinned on the map
 * and filtered by radius.
 */
public class Item {

    private long id;
    private String postType;     // "Lost" or "Found"
    private String name;
    private String phone;
    private String description;
    private String date;
    private String location;     // human-readable address string
    private String category;
    private String imagePath;
    private long timestamp;
    private double latitude;     // NEW in 9.1
    private double longitude;    // NEW in 9.1

    public Item() { /* empty for cursor mapping */ }

    public Item(long id, String postType, String name, String phone, String description,
                String date, String location, String category, String imagePath,
                long timestamp, double latitude, double longitude) {
        this.id = id;
        this.postType = postType;
        this.name = name;
        this.phone = phone;
        this.description = description;
        this.date = date;
        this.location = location;
        this.category = category;
        this.imagePath = imagePath;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // ---- getters / setters ----

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getPostType() { return postType; }
    public void setPostType(String postType) { this.postType = postType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    /**
     * Returns true only if this advert has a real coordinate attached.
     * Items created in 7.1 (before the geo update) will have 0,0 which is
     * valid-ish but actually points to the Gulf of Guinea, so we treat it
     * as "no location".
     */
    public boolean hasLocation() {
        return latitude != 0.0 || longitude != 0.0;
    }
}
