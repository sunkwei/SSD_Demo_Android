package cc.hetinsow.sunkw.ssd_demo;

public class ActionBox {
    public int catalog;
    public String title;
    public float score;
    public float x1, y1, x2, y2;

    public ActionBox(int catalog, String title, float score, float x1, float y1, float x2, float y2) {
        this.catalog = catalog;
        this.title = title;
        this.score = score;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
    }
}
