package com.mqd.updatejava.core;

/**
 * GitHub Releases API 数据模型。
 */
public class GithubRelease {
    public String tagName = "";
    public String name = "";
    public String body = "";
    public String htmlUrl = "";
    public Asset[] assets = new Asset[0];

    public static class Asset {
        public String name = "";
        public String browserDownloadUrl = "";
        public long size = 0;
    }
}
