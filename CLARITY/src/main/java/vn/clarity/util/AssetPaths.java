package vn.clarity.util;

import java.util.Map;

public final class AssetPaths {
    private AssetPaths() {}

    private static final String FALLBACK = "/assets/img/favicon.svg";
    private static final Map<String, String> LEGACY = Map.ofEntries(
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-1.jpg", "/assets/img/products/product-01.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-2.jpg", "/assets/img/products/product-02.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-3.jpg", "/assets/img/products/product-03.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-4.jpg", "/assets/img/products/product-04.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-5.jpg", "/assets/img/products/product-05.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-6.jpg", "/assets/img/products/product-06.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-7.jpg", "/assets/img/products/product-07.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-8.jpg", "/assets/img/products/product-08.jpg"),
        Map.entry("/img/danh-sach-tim-kiem-loc-cosplay-cosplay-9.jpg", "/assets/img/products/product-09.jpg"),
        Map.entry("/img/favicon.svg", FALLBACK)
    );

    public static String normalize(String path) {
        if (path == null || path.isBlank()) return FALLBACK;
        if (path.startsWith("/assets/img/")) return path;
        return LEGACY.getOrDefault(path, FALLBACK);
    }
}
