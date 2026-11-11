package com.shadhinkhan.grabit;

import java.util.ArrayList;

/** One downloadable format option. */
public class Fmt {
    public String id, ext, kind, quality, size;
    public boolean watermarked;
    public String directUrl;              // direct stream/download URL (tikwm or innertube)
    public String httpUa;                 // innertube: User-Agent that must accompany directUrl
    public String container;              // innertube: real container of directUrl (mp4/webm)
    public String audioUrl;               // innertube video-only: companion audio stream URL
    public String audioContainer;         // companion audio container (mp4/webm)
    public ArrayList<String> imageUrls;   // tikwm photo posts
}
